package com.example.messenger

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView


data class MenuItemData(
    val title: String,
    val backgroundRes: Int
)

class PopupMenuWallpaperAdapter(
    private val items: List<MenuItemData>,
    private val onItemClick: (MenuItemData) -> Unit
) : RecyclerView.Adapter<PopupMenuWallpaperAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.menu_item_text)
        val layout: LinearLayout = view.findViewById(R.id.menu_item_layout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.wallpaper_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.textView.text = item.title
        holder.layout.setBackgroundResource(item.backgroundRes)
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }
}