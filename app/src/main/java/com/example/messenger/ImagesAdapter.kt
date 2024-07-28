package com.example.messenger

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

//todo тут все неправильно
class ImagesAdapter(private val imageUrls: List<String>) : RecyclerView.Adapter<ImagesAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView = itemView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image_sender, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        // Загрузка изображения с помощью Glide
//        Glide.with(holder.itemView.context)
//            .load(imageUrls[position])
//            .into(holder.imageView)
    }

    override fun getItemCount() = imageUrls.size
}
