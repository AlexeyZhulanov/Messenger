package com.example.messenger.voicerecorder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class VoiceRecorder(private val context: Context) {
    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_COUNT = 1
        private const val BIT_RATE = 24000
    }

    private val isRecording = AtomicBoolean(false)

    private var currentFile: File? = null
    private val waveform = ArrayList<Int>()

    private var totalSamplesWritten: Long = 0L

    private val lifecycleMutex = Mutex()
    private var activeJob: Job? = null

    data class RecordingResult(
        val file: File,
        val waveform: IntArray,
        val durationMs: Long
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as RecordingResult

            if (durationMs != other.durationMs) return false
            if (file != other.file) return false
            if (!waveform.contentEquals(other.waveform)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = durationMs.hashCode()
            result = 31 * result + file.hashCode()
            result = 31 * result + waveform.contentHashCode()
            return result
        }
    }

    private class EncoderState {
        var trackIndex: Int = -1
        var muxerStarted: Boolean = false
    }

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun start(file: File, scope: CoroutineScope, onError: (String) -> Unit = {}) {
        if (!hasPermission()) {
            onError("RECORD_AUDIO permission not granted")
            return
        }
        scope.launch {
            lifecycleMutex.withLock {

                if (activeJob?.isActive == true) {
                    onError("Recording already in progress")
                    return@withLock
                }
                waveform.clear()
                totalSamplesWritten = 0
                currentFile = file
                isRecording.set(true)

                activeJob = launch(Dispatchers.IO) {
                    @Suppress("MissingPermission")
                    recordInternal(file)
                }
            }
        }
    }

    suspend fun stop(): RecordingResult? {
        return lifecycleMutex.withLock {

            val job = activeJob ?: return@withLock null
            if (!job.isActive) return@withLock null

            isRecording.set(false)
            job.join()
            activeJob = null

            val file = currentFile ?: return@withLock null
            val optimized = downsampleWaveform(waveform)

            val durationMs = (totalSamplesWritten * 1000L) / SAMPLE_RATE

            RecordingResult(
                file = file,
                waveform = optimized,
                durationMs = durationMs
            )
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun recordInternal(outputFile: File) {

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBuffer == AudioRecord.ERROR || minBuffer == AudioRecord.ERROR_BAD_VALUE) {
            throw IllegalStateException("Invalid AudioRecord buffer size")
        }

        val bufferSize = minBuffer * 2

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("AudioRecord initialization failed")
        }

        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            SAMPLE_RATE,
            CHANNEL_COUNT
        ).apply {
            setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC
            )
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize)
        }

        val encoder = MediaCodec.createEncoderByType(
            MediaFormat.MIMETYPE_AUDIO_AAC
        )

        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(
            outputFile.absolutePath,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )

        val bufferInfo = MediaCodec.BufferInfo()
        val pcmBuffer = ShortArray(bufferSize / 2)

        val encoderState = EncoderState()

        totalSamplesWritten = 0L

        audioRecord.startRecording()

        if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            audioRecord.release()
            throw IllegalStateException("Microphone failed to start recording")
        }

        try {

            while (isRecording.get()) {

                val read = audioRecord.read(
                    pcmBuffer,
                    0,
                    pcmBuffer.size
                )

                if (read <= 0) continue

                // ---- waveform ----
                collectWaveform(pcmBuffer, read)

                // ---- feed encoder ----
                val inputIndex = encoder.dequeueInputBuffer(10_000)

                if (inputIndex >= 0) {

                    val inputBuffer = encoder.getInputBuffer(inputIndex)
                        ?: continue

                    inputBuffer.clear()

                    writeShortsToBuffer(
                        inputBuffer,
                        pcmBuffer,
                        read
                    )

                    val pts =
                        (totalSamplesWritten * 1_000_000L) / SAMPLE_RATE

                    encoder.queueInputBuffer(
                        inputIndex,
                        0,
                        read * 2,
                        pts,
                        0
                    )

                    totalSamplesWritten += read
                }

                drainEncoder(
                    encoder,
                    muxer,
                    bufferInfo,
                    encoderState
                )
            }

            // ---- End Of Stream ----
            val inputIndex = encoder.dequeueInputBuffer(10_000)
            if (inputIndex >= 0) {

                val pts =
                    (totalSamplesWritten * 1_000_000L) / SAMPLE_RATE

                encoder.queueInputBuffer(
                    inputIndex,
                    0,
                    0,
                    pts,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
            }

            // финальный drain
            drainEncoder(
                encoder,
                muxer,
                bufferInfo,
                encoderState
            )

        } finally {

            try {
                audioRecord.stop()
            } catch (_: Exception) {}
            audioRecord.release()

            try {
                encoder.stop()
            } catch (_: Exception) {}
            encoder.release()

            if (encoderState.muxerStarted) {
                try {
                    muxer.stop()
                } catch (_: Exception) {}
            }

            muxer.release()
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        state: EncoderState
    ) {
        while (true) {

            val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)

            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    break
                }

                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {

                    if (state.muxerStarted) {
                        throw IllegalStateException("Format changed twice")
                    }

                    val newFormat = encoder.outputFormat
                    state.trackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    state.muxerStarted = true
                }

                outIndex >= 0 -> {

                    val encodedData = encoder.getOutputBuffer(outIndex)
                        ?: throw RuntimeException("Encoder output buffer was null")

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        // codec config handled automatically by muxer
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size > 0) {

                        if (!state.muxerStarted) {
                            throw IllegalStateException("Muxer not started")
                        }

                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)

                        muxer.writeSampleData(
                            state.trackIndex,
                            encodedData,
                            bufferInfo
                        )
                    }

                    encoder.releaseOutputBuffer(outIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                }
            }
        }
    }


    private fun writeShortsToBuffer(
        buffer: ByteBuffer,
        data: ShortArray,
        length: Int
    ) {
        for (i in 0 until length) {
            val value = data[i].toInt()
            buffer.put((value and 0xff).toByte())
            buffer.put(((value shr 8) and 0xff).toByte())
        }
    }

    // RMS каждые ~20мс
    private var rmsAccumulator = 0.0
    private var rmsCount = 0
    private val samplesPerWavePoint = SAMPLE_RATE / 50 // 20мс

    private fun collectWaveform(pcm: ShortArray, length: Int) {
        for (i in 0 until length) {
            val s = pcm[i].toInt()
            rmsAccumulator += (s * s)
            rmsCount++

            if (rmsCount >= samplesPerWavePoint) {
                val rms = sqrt(rmsAccumulator / rmsCount).toInt()
                waveform.add(rms)
                rmsAccumulator = 0.0
                rmsCount = 0
            }
        }
    }

    private fun downsampleWaveform(raw: List<Int>, target: Int = 100): IntArray {
        if (raw.isEmpty()) return intArrayOf()
        if (raw.size <= target) return raw.toIntArray()

        val result = IntArray(target)
        val segment = raw.size / target.toFloat()

        for (i in 0 until target) {
            val start = (i * segment).toInt()
            val end = ((i + 1) * segment).toInt().coerceAtMost(raw.size)

            var max = 0
            for (j in start until end) {
                if (raw[j] > max) max = raw[j]
            }
            result[i] = max
        }

        return result
    }
}