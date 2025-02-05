package com.example.messenger

import android.annotation.SuppressLint
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.messenger.databinding.ItemVoiceBinding
import com.masoudss.lib.SeekBarOnProgressChanged
import com.masoudss.lib.WaveformSeekBar
import java.io.File

class VoicesAdapter(
    private val newsViewModel: NewsViewModel
) : RecyclerView.Adapter<VoicesAdapter.VoicesViewHolder>() {

    private var isPlaying: Boolean = false
    private val handler = Handler(Looper.getMainLooper())

    var voicePaths: List<String> = listOf()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getItemCount(): Int = voicePaths.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VoicesViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemVoiceBinding.inflate(inflater, parent, false)
        return VoicesViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VoicesViewHolder, position: Int) {
        val voicePath = voicePaths[position]
        val file = File(voicePath)
        with(holder.binding) {
            val mediaPlayer = MediaPlayer().apply {
                setDataSource(voicePath)
                prepare()
            }
            val duration = mediaPlayer.duration
            waveformSeekBar.setSampleFrom(file)
            waveformSeekBar.maxProgress = duration.toFloat()
            timeVoiceTextView.text = newsViewModel.formatTime(duration.toLong())

            val updateSeekBarRunnable = object : Runnable {
                override fun run() {
                    if (isPlaying && mediaPlayer.isPlaying) {
                        val currentPosition = mediaPlayer.currentPosition.toFloat()
                        waveformSeekBar.progress = currentPosition
                        timeVoiceTextView.text = newsViewModel.formatTime(currentPosition.toLong())
                        handler.postDelayed(this, 100)
                    }
                }
            }
            playButton.setOnClickListener {
                if (!isPlaying) {
                    mediaPlayer.start()
                    playButton.setImageResource(R.drawable.ic_pause)
                    isPlaying = true
                    handler.post(updateSeekBarRunnable) // Запуск обновления SeekBar
                } else {
                    mediaPlayer.pause()
                    playButton.setImageResource(R.drawable.ic_play)
                    isPlaying = false
                    handler.removeCallbacks(updateSeekBarRunnable) // Остановка обновления SeekBar
                }
            }
            // Обработка изменения положения SeekBar
            waveformSeekBar.onProgressChanged = object :
                SeekBarOnProgressChanged {
                override fun onProgressChanged(waveformSeekBar: WaveformSeekBar, progress: Float, fromUser: Boolean) {
                    if (fromUser) {
                        mediaPlayer.seekTo(progress.toInt())
                        timeVoiceTextView.text = newsViewModel.formatTime(progress.toLong())
                    }
                }
            }
            mediaPlayer.setOnCompletionListener {
                playButton.setImageResource(R.drawable.ic_play)
                waveformSeekBar.progress = 0f
                timeVoiceTextView.text = newsViewModel.formatTime(duration.toLong())
                isPlaying = false
                handler.removeCallbacks(updateSeekBarRunnable)
            }
        }
    }

    class VoicesViewHolder(
        val binding: ItemVoiceBinding
    ) : RecyclerView.ViewHolder(binding.root)
}