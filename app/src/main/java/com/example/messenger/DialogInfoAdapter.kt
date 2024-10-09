package com.example.messenger

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.messenger.model.MediaItem

class DialogInfoAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val mediaItems = mutableListOf<MediaItem>()

    fun setMediaItems(items: List<MediaItem>) {
        mediaItems.addAll(items)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return mediaItems[position].type
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            MediaItem.TYPE_MEDIA -> {
                val view = inflater.inflate(R.layout.item_media, parent, false)
                MediaViewHolder(view)
            }
            MediaItem.TYPE_FILE -> {
                val view = inflater.inflate(R.layout.item_file, parent, false)
                FileViewHolder(view)
            }
            MediaItem.TYPE_AUDIO -> {
                val view = inflater.inflate(R.layout.item_audio, parent, false)
                AudioViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = mediaItems[position]
        when (holder) {
            is MediaViewHolder -> holder.bind(item.content)
            is FileViewHolder -> holder.bind(item.content)
            is AudioViewHolder -> holder.bind(item.content)
        }
    }

    override fun getItemCount(): Int = mediaItems.size

    class MediaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.imageView)

        fun bind(imageUrl: String) {
            // Загрузка изображения, например через Glide
            Glide.with(itemView.context).load(imageUrl).into(imageView)
        }
    }

    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val fileNameView: TextView = itemView.findViewById(R.id.fileNameView)

        fun bind(fileName: String) {
            fileNameView.text = fileName
        }
    }

    class AudioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val audioNameView: TextView = itemView.findViewById(R.id.audioNameView)

        fun bind(audioName: String) {
            audioNameView.text = audioName
        }
    }
}