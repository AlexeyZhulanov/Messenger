package com.example.messenger

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.messenger.databinding.ItemImageBinding
import com.example.messenger.picker.DateUtils
import com.luck.picture.lib.config.PictureMimeType
import com.luck.picture.lib.config.SelectMimeType
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
        val chooseModel = localMedia.chooseModel
        val duration = localMedia.duration
        val path = localMedia.availablePath
        with(holder.binding) {
            tvDuration.visibility = if (PictureMimeType.isHasVideo(localMedia.mimeType)) View.VISIBLE else View.GONE
            if(chooseModel == SelectMimeType.ofAudio()) {
                tvDuration.visibility = View.VISIBLE
                tvDuration.setCompoundDrawablesRelativeWithIntrinsicBounds(com.luck.picture.lib.R.drawable.ps_ic_audio, 0, 0, 0)
            } else {
                tvDuration.setCompoundDrawablesRelativeWithIntrinsicBounds(com.luck.picture.lib.R.drawable.ps_ic_video, 0, 0, 0)
            }
            tvDuration.text = (DateUtils.formatDurationTime(duration))
            if(chooseModel == SelectMimeType.ofAudio()) {
                photoImageView.setImageResource(com.luck.picture.lib.R.drawable.ps_audio_placeholder)
            } else {
                Glide.with(context)
                    .load(if (PictureMimeType.isContent(path) && !localMedia.isCut && !localMedia.isCompressed) Uri.parse(path) else path)
                    .centerCrop()
                    .placeholder(com.example.messenger.R.color.app_color_f6)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(photoImageView)
            }
            photoImageView.tag = localMedia
            deleteButton.tag = localMedia
        }
    }

    override fun getItemCount(): Int = images.size

    class ImageViewHolder(
        val binding: ItemImageBinding
    ) : RecyclerView.ViewHolder(binding.root)
}