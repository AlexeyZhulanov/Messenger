package com.example.messenger

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.messenger.databinding.ItemFileBinding
import com.example.messenger.databinding.ItemMediaBinding
import com.example.messenger.databinding.ItemVoiceBinding
import com.example.messenger.model.MediaItem
import com.luck.picture.lib.entity.LocalMedia
import java.io.File

interface DialogActionListener {
    fun onItemClicked(position: Int, filename: String, localMedias: ArrayList<LocalMedia>)
}

class DialogInfoAdapter(
    private val context: Context,
    private val imageSize: Int,
    private val messageViewModel: MessageViewModel,
    private val actionListener: DialogActionListener
): RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val mediaItems = mutableListOf<MediaItem>()
    private val localMedias = arrayListOf<LocalMedia>()

    @SuppressLint("NotifyDataSetChanged")
    fun setMediaItems(items: List<MediaItem>) {
        localMedias.clear()
        mediaItems.clear()
        mediaItems.addAll(items)
        if(items[0].type == 0) {
            items.forEach {
                localMedias.add(messageViewModel.fileToLocalMedia(File(it.content)))
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return mediaItems[position].type
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            MediaItem.TYPE_MEDIA -> MediaViewHolder(
                ItemMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            MediaItem.TYPE_FILE -> FileViewHolder(
                ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            MediaItem.TYPE_AUDIO -> AudioViewHolder(
                ItemVoiceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = mediaItems[position]
        when (holder) {
            is MediaViewHolder -> holder.bind(item.content, position)
            is FileViewHolder -> holder.bind(item.content)
            is AudioViewHolder -> holder.bind(item.content)
        }
    }
    override fun getItemCount(): Int = mediaItems.size

    inner class MediaViewHolder(private val binding: ItemMediaBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(content: String, position: Int) {
            binding.photoImageView.layoutParams.width = imageSize
            binding.photoImageView.layoutParams.height = imageSize
            val(originalFilename, duration) = messageViewModel.parsePreviewFilename(content)
            val file = File(content)
            if (file.exists()) {
                val uri = Uri.fromFile(file)
                Glide.with(context)
                    .load(uri)
                    .placeholder(R.color.app_color_f6)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(binding.photoImageView)

                if(duration != null) {
                    binding.tvDuration.text = duration
                    binding.tvDuration.visibility = View.VISIBLE
                }
                binding.photoImageView.setOnClickListener {
                    actionListener.onItemClicked(position, originalFilename, ArrayList(localMedias))
                }
            } else {
                binding.icError.visibility = View.VISIBLE
            }
        }
    }

    inner class FileViewHolder(private val binding: ItemFileBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(content: String) {

        }
    }

    inner class AudioViewHolder(private val binding: ItemVoiceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(content: String) {

        }
    }

}