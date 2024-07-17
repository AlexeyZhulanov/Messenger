package com.example.messenger

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

data class ColorThemeMenuItem(
    val mainColorRes: Int,
    val secondColorRes: Int,
    val themeNumber: Int
)

class ColorThemeMenuAdapter(
    private val menuItems: List<ColorThemeMenuItem>,
    private val onItemClick: (ColorThemeMenuItem) -> Unit
) : RecyclerView.Adapter<ColorThemeMenuAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.color_theme_menu_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val menuItem = menuItems[position]

        // Установка фоновых цветов для элементов меню
        holder.mainColorView.setBackgroundResource(menuItem.mainColorRes)
        holder.secondColorView.setBackgroundResource(menuItem.secondColorRes)

        holder.itemView.setOnClickListener {
            onItemClick(menuItem)
        }
    }

    override fun getItemCount(): Int {
        return menuItems.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val mainColorView: View = itemView.findViewById(R.id.menu_theme_main_icon)
        val secondColorView: View = itemView.findViewById(R.id.menu_theme_second_icon)
    }
}