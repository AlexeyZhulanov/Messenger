package com.example.messenger

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.messenger.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

interface DialogActionListener {
    fun onItemClicked()
}

class DialogInfoAdapter(
    private val context: Context,
    private val actionListener: DialogActionListener
): RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val mediaItems = mutableListOf<MediaItem>()

    @SuppressLint("NotifyDataSetChanged")
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
                val view = inflater.inflate(R.layout.item_voice, parent, false)
                AudioViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = mediaItems[position]
        when (holder) {
            is MediaViewHolder -> holder.bind(item.content, context)
            is FileViewHolder -> holder.bind(item.content)
            is AudioViewHolder -> holder.bind(item.content)
        }
    }

    override fun getItemCount(): Int = mediaItems.size

    class MediaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.photoImageView)

        fun bind(imageUrl: String, context: Context) {
            val file = File(imageUrl)
            if (file.exists()) {
                val uri = Uri.fromFile(file)
                Glide.with(context)
                    .load(uri)
                    .apply(RequestOptions.circleCropTransform())
                    .placeholder(R.color.app_color_f6)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(imageView)
            } else {
                val errorImageView: ImageView = itemView.findViewById(R.id.errorImageView)
                errorImageView.visibility = View.VISIBLE
            }
        }
    }

    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val fileNameView: TextView = itemView.findViewById(R.id.fileNameTextView)

        fun bind(fileName: String) {
            fileNameView.text = fileName
        }
    }

    class AudioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bind(audioName: String) {
            // todo
        }
    }
}