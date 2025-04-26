package com.example.messenger

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class ColorThemeMenuItem(
    val mainColorRes: Int,
    val secondColorRes: Int,
    val themeNumber: Int,
    var isChecked: Boolean
)

class ColorThemeMenuAdapter(
    private val onItemClick: (ColorThemeMenuItem) -> Unit
) : RecyclerView.Adapter<ColorThemeMenuAdapter.ViewHolder>() {

    var menuItems: List<ColorThemeMenuItem> = listOf()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_color_theme, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val menuItem = menuItems[position]
        val text = "Тема ${menuItem.themeNumber}"

        with(holder) {
            mainColorView.setBackgroundResource(menuItem.mainColorRes)
            secondColorView.setBackgroundResource(menuItem.secondColorRes)

            textView.text = text
            radioButton.isChecked = menuItem.isChecked

            itemView.setOnClickListener {
                onItemClick(menuItem)
            }
        }
    }

    override fun getItemCount(): Int {
        return menuItems.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val mainColorView: View = itemView.findViewById(R.id.view1)
        val secondColorView: View = itemView.findViewById(R.id.view2)
        val textView: TextView = itemView.findViewById(R.id.radioTextView)
        val radioButton: RadioButton = itemView.findViewById(R.id.radio)
    }
}