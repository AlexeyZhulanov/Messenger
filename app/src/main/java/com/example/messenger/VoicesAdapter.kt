package com.example.messenger

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.messenger.databinding.ItemVoiceBinding
import com.example.messenger.states.AudioPlaybackState
import com.example.messenger.states.VoiceItem
import com.example.messenger.utils.formatTime
import com.masoudss.lib.SeekBarOnProgressChanged
import com.masoudss.lib.WaveformSeekBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

interface VoicesActionListener {
    fun onPlayClick(filePath: String)
    fun onVoiceSeek(filePath: String, progress: Int)
}

object VoiceDiffCallback : DiffUtil.ItemCallback<VoiceItem>() {
    override fun areItemsTheSame(oldItem: VoiceItem, newItem: VoiceItem): Boolean {
        return oldItem.localPath == newItem.localPath
    }
    override fun areContentsTheSame(oldItem: VoiceItem, newItem: VoiceItem): Boolean {
        return oldItem == newItem
    }
}

class VoicesAdapter(
    private val playbackStateFlow: StateFlow<AudioPlaybackState>,
    private val scope: CoroutineScope,
    private val actionListener: VoicesActionListener
) : ListAdapter<VoiceItem, VoicesAdapter.VoicesViewHolder>(VoiceDiffCallback) {


    override fun getItemCount(): Int = currentList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VoicesViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemVoiceBinding.inflate(inflater, parent, false)
        return VoicesViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VoicesViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: VoicesViewHolder) {
        super.onViewRecycled(holder)
        holder.unbind()
    }

    inner class VoicesViewHolder(val binding: ItemVoiceBinding) : RecyclerView.ViewHolder(binding.root) {
        private var currentPath: String? = null
        private var playbackJob: Job? = null

        init {
            binding.playButton.setOnClickListener {
                currentPath?.let { actionListener.onPlayClick(it) }
            }

            binding.waveformSeekBar.onProgressChanged = object : SeekBarOnProgressChanged {
                override fun onProgressChanged(waveformSeekBar: WaveformSeekBar, progress: Float, fromUser: Boolean) {
                    if (fromUser) {
                        currentPath?.let {
                            actionListener.onVoiceSeek(it, progress.toInt())
                        }
                    }
                }
            }
        }

        fun bind(voice: VoiceItem) {
            currentPath = voice.localPath
            binding.waveformSeekBar.setSampleFrom(voice.sample.toIntArray())
            binding.waveformSeekBar.maxProgress = voice.duration.toFloat()
            val st = formatTime(voice.duration)
            binding.timeVoiceTextView.text = st

            observePlaybackState()
        }

        private fun observePlaybackState() {
            // Отменяем старую работу, если ViewHolder переиспользуется
            playbackJob?.cancel()

            playbackJob = scope.launch {
                playbackStateFlow.collectLatest { state ->
                    if (state.playingPath == currentPath) {
                        binding.waveformSeekBar.progress = state.progress.toFloat()
                        binding.timeVoiceTextView.text = formatTime(state.progress.toLong())
                        if (state.isPlaying) {
                            binding.playButton.setImageResource(R.drawable.ic_pause)
                        } else {
                            binding.playButton.setImageResource(R.drawable.ic_play)
                        }
                    } else {
                        binding.waveformSeekBar.progress = 0f
                        binding.playButton.setImageResource(R.drawable.ic_play)
                        binding.timeVoiceTextView.text = formatTime(binding.waveformSeekBar.maxProgress.toLong())
                    }
                }
            }
        }

        fun unbind() {
            playbackJob?.cancel() // Остановка подписки при скролле
        }
    }
}