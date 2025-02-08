package com.example.messenger

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.messenger.databinding.ItemNewsRecBinding

interface NewsRecActionListener {
    fun onItemDeleteClicked(position: Int)
}

class NewsRecAdapter(
    private val actionListener: NewsRecActionListener
) : RecyclerView.Adapter<NewsRecAdapter.NewsRecViewHolder>() {

    var names: MutableList<String> = mutableListOf()
        set(value) {
            field = value
            notifyItemRangeChanged(0, value.size)
        }

    fun addItem(item: String) {
        names.add(item)
        notifyItemInserted(names.size - 1)
    }

    fun removeItem(position: Int) {
        names.removeAt(position)
        notifyItemRemoved(position)
    }

    override fun getItemCount(): Int = names.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsRecViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemNewsRecBinding.inflate(inflater, parent, false)
        return NewsRecViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NewsRecViewHolder, position: Int) {
        val name = names[position]
        with(holder.binding) {
            nameTextView.text = name
            deleteButton.setOnClickListener {
                actionListener.onItemDeleteClicked(position)
            }
        }
    }

    class NewsRecViewHolder(
        val binding: ItemNewsRecBinding
    ) : RecyclerView.ViewHolder(binding.root)
}