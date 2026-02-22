package com.example.messenger

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.messenger.databinding.ItemImagesBinding
import com.example.messenger.picker.DateUtils
import com.luck.picture.lib.config.PictureMimeType
import com.luck.picture.lib.config.SelectMimeType
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.example.messenger.states.ImageItem

interface ImagesActionListener {
    fun onImageClicked(position: Int)
    fun onLongImageClicked()
}

object DiffCallback : DiffUtil.ItemCallback<ImageItem>() {
    override fun areItemsTheSame(oldItem: ImageItem, newItem: ImageItem): Boolean {
        return oldItem.localPath == newItem.localPath
    }
    override fun areContentsTheSame(oldItem: ImageItem, newItem: ImageItem): Boolean {
        return oldItem == newItem
    }
}

class ImagesAdapter(private val actionListener: ImagesActionListener) :
    ListAdapter<ImageItem, ImagesAdapter.ImagesViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImagesViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemImagesBinding.inflate(inflater, parent, false)
        return ImagesViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImagesViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun getItemCount() = currentList.size

    inner class ImagesViewHolder(val binding: ItemImagesBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                actionListener.onImageClicked(bindingAdapterPosition)
            }

            binding.root.setOnLongClickListener {
                actionListener.onLongImageClicked()
                true
            }
        }

        fun bind(item: ImageItem) {
            binding.tvDuration.isVisible = PictureMimeType.isHasVideo(item.mimeType)
            val chooseModel = PictureMimeType.getMimeType(item.mimeType)
            if (chooseModel == SelectMimeType.ofAudio()) {
                binding.tvDuration.isVisible = true
                binding.tvDuration.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    com.luck.picture.lib.R.drawable.ps_ic_audio, 0, 0, 0)
            } else {
                binding.tvDuration.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    com.luck.picture.lib.R.drawable.ps_ic_video, 0, 0, 0)
            }
            binding.tvDuration.text = (DateUtils.formatDurationTime(item.duration))
            if (chooseModel == SelectMimeType.ofAudio()) {
                binding.photoImageView.setImageResource(com.luck.picture.lib.R.drawable.ps_audio_placeholder)
            } else {
                Glide.with(binding.photoImageView)
                    .load(item.localPath)
                    .centerCrop()
                    .placeholder(R.color.app_color_f6)
                    .dontAnimate()
                    .into(binding.photoImageView)
            }
        }
    }
}
