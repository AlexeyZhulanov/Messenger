package com.example.messenger.voicerecorder;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;

import static android.os.Process.THREAD_PRIORITY_AUDIO;
import static com.example.messenger.voicerecorder.Constants.TAG;
import static android.os.Process.setThreadPriority;

import androidx.core.app.ActivityCompat;

import com.example.messenger.BaseChatFragment;
import com.example.messenger.BottomSheetNewsFragment;


public class AudioRecorder {

    /** The stream to write to. */
    private final OutputStream mOutputStream;

    /**
     * If true, the background thread will continue to loop and record audio. Once false, the thread
     * will shut down.
     */
    private volatile boolean mAlive;

    /** The background thread recording audio for us. */
    private Thread mThread;

    /**
     * A simple audio recorder.
     *
     * @param file The output stream of the recording.
     */
    public AudioRecorder(ParcelFileDescriptor file) {
        mOutputStream = new ParcelFileDescriptor.AutoCloseOutputStream(file);
    }

    /** @return True if actively recording. False otherwise. */
    public boolean isRecording() {
        return mAlive;
    }

    /** Starts recording audio. */
    public void start(BaseChatFragment fragment, BottomSheetNewsFragment fragment2) {
        if (isRecording()) {
            Log.w(TAG, "Already running");
            return;
        }
        Context context;
        if (fragment != null) {
            context = fragment.requireContext();
        } else if (fragment2 != null) {
            context = fragment2.requireContext();
        } else {
            throw new IllegalArgumentException("Both fragments cannot be null");
        }
        mAlive = true;
        mThread =
                new Thread() {
                    @Override
                    public void run() {
                        setThreadPriority(THREAD_PRIORITY_AUDIO);

                        Buffer buffer = new Buffer();
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            // TODO: Consider calling
                            //    ActivityCompat#requestPermissions
                            // here to request the missing permissions, and then overriding
                            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                            //                                          int[] grantResults)
                            // to handle the case where the user grants the permission. See the documentation
                            // for ActivityCompat#requestPermissions for more details.
                            return;
                        }
                        AudioRecord record =
                                new AudioRecord(
                                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                                        buffer.sampleRate,
                                        AudioFormat.CHANNEL_IN_MONO,
                                        AudioFormat.ENCODING_PCM_16BIT,
                                        buffer.size);

                        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                            Log.w(TAG, "Failed to start recording");
                            mAlive = false;
                            return;
                        }

                        record.startRecording();

                        // While we're running, we'll read the bytes from the AudioRecord and write them
                        // to our output stream.
                        try {
                            while (isRecording()) {
                                int len = record.read(buffer.data, 0, buffer.size);
                                if (len >= 0 && len <= buffer.size) {
                                    mOutputStream.write(buffer.data, 0, len);
                                    mOutputStream.flush();
                                } else {
                                    Log.w(TAG, "Unexpected length returned: " + len);
                                }
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Exception with recording stream", e);
                        } finally {
                            stopInternal();
                            try {
                                record.stop();
                            } catch (IllegalStateException e) {
                                Log.e(TAG, "Failed to stop AudioRecord", e);
                            }
                            record.release();
                        }
                    }
                };
        mThread.start();
    }

    private void stopInternal() {
        mAlive = false;
        try {
            mOutputStream.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to close output stream", e);
        }
    }

    /** Stops recording audio. */
    public void stop() {
        stopInternal();
        try {
            mThread.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while joining AudioRecorder thread", e);
            Thread.currentThread().interrupt();
        }
    }

    private static class Buffer extends AudioBuffer {
        @Override
        protected boolean validSize(int size) {
            return size != AudioRecord.ERROR && size != AudioRecord.ERROR_BAD_VALUE;
        }

        @Override
        protected int getMinBufferSize(int sampleRate) {
            return AudioRecord.getMinBufferSize(
                    sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        }
    }
}