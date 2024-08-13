package com.example.messenger

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.messenger.databinding.ItemImageBinding
import com.luck.picture.lib.entity.LocalMedia


interface ImageActionListener {
    fun onImageClicked(image: LocalMedia, position: Int)
    fun onDeleteImage(position: Int)
}

class ImageAdapter(
    private val context: Context,
    private val actionListener: ImageActionListener) :
    RecyclerView.Adapter<ImageAdapter.ImageViewHolder>(), View.OnClickListener {

    var images: ArrayList<LocalMedia> = arrayListOf()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }


    fun delete(position: Int) {
        try {
            if (position != RecyclerView.NO_POSITION && images.count() > position) {
                images.removeAt(position)
                notifyItemRemoved(position)
                notifyItemRangeChanged(position, images.count())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun remove(position: Int) {
        if (position < images.count()) {
            images.removeAt(position)
        }
    }

    fun getData(): ArrayList<LocalMedia> {
        return images
    }

    override fun onClick(v: View) {
        val image = v.tag as LocalMedia
        when(v.id) {
            R.id.delete_button -> {
                actionListener.onDeleteImage(images.indexOf(image))
            }
            else -> { actionListener.onImageClicked(image, images.indexOf(image)) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemImageBinding.inflate(inflater, parent, false)
        binding.photoImageView.setOnClickListener(this)
        binding.deleteButton.setOnClickListener(this)
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val localMedia = images[position]
        with(holder.binding) {
            photoImageView.tag = localMedia
            deleteButton.tag = localMedia
        }
        val uri = Uri.parse(localMedia.path) // get Uri from LocalMedia
        Glide.with(context).load(uri).into(holder.binding.photoImageView)
    }

    override fun getItemCount(): Int = images.size

    class ImageViewHolder(
        val binding: ItemImageBinding
    ) : RecyclerView.ViewHolder(binding.root)
}