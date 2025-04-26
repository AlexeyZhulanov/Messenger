package com.example.messenger

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView


data class WallpaperMenuItem(
    val backgroundRes: Int,
    val wallpaperName: String,
    val themeNumber: Int,
    var isChecked: Boolean
)


class WallpaperAdapter(
    private val onItemClick: (WallpaperMenuItem) -> Unit
) : RecyclerView.Adapter<WallpaperAdapter.ViewHolder>() {

    var items: List<WallpaperMenuItem> = listOf()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val wallpaperView: View = itemView.findViewById(R.id.wallpaper)
        val textView: TextView = itemView.findViewById(R.id.radioTextView)
        val radioButton: RadioButton = itemView.findViewById(R.id.radio)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_wallpaper, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val text = "Обои ${item.themeNumber}"
        with(holder) {
            textView.text = text
            wallpaperView.setBackgroundResource(item.backgroundRes)
            radioButton.isChecked = item.isChecked
            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }
}