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
import com.example.messenger.databinding.ItemImagesBinding
import com.example.messenger.picker.DateUtils
import com.luck.picture.lib.config.PictureMimeType
import com.luck.picture.lib.config.SelectMimeType
import com.luck.picture.lib.entity.LocalMedia

interface ImagesActionListener {
    fun onImageClicked(images: ArrayList<LocalMedia>, position: Int)
    fun onLongImageClicked()
}

class ImagesAdapter(
    private val context: Context,
    private val actionListener: ImagesActionListener) :
    RecyclerView.Adapter<ImagesAdapter.ImagesViewHolder>(), View.OnClickListener, View.OnLongClickListener {

    var images: ArrayList<LocalMedia> = arrayListOf()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onClick(v: View) {
        val viewHolder = v.tag as ImagesViewHolder
        val position = viewHolder.bindingAdapterPosition
        actionListener.onImageClicked(images, position)
    }

    override fun onLongClick(v: View): Boolean {
        actionListener.onLongImageClicked()
        return true
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImagesViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemImagesBinding.inflate(inflater, parent, false)
        binding.root.setOnClickListener(this)
        binding.root.setOnLongClickListener(this)
        return ImagesViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImagesViewHolder, position: Int) {
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
                    .placeholder(R.color.app_color_f6)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(photoImageView)
            }
            holder.itemView.tag = holder
        }
    }

    override fun getItemCount() = images.size

    class ImagesViewHolder(
        val binding: ItemImagesBinding
    ) : RecyclerView.ViewHolder(binding.root)
}
