package com.example.messenger

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.messenger.databinding.ItemFileBinding
import java.io.File

interface FilesActionListener {
    fun onFileOpenClicked(file: File)
}

class FilesAdapter(
    private val newsViewModel: NewsViewModel,
    private val actionListener: FilesActionListener
) : RecyclerView.Adapter<FilesAdapter.FilesViewHolder>() {

    var files: List<File> = listOf()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getItemCount(): Int = files.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilesViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemFileBinding.inflate(inflater, parent, false)
        return FilesViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FilesViewHolder, position: Int) {
        val file = files[position]
        with(holder.binding) {
            fileNameTextView.text = file.name
            fileSizeTextView.text = newsViewModel.formatFileSize(file.length())
            fileButton.setOnClickListener {
                actionListener.onFileOpenClicked(file)
            }
            holder.itemView.tag = holder
        }
    }

    class FilesViewHolder(
        val binding: ItemFileBinding
    ) : RecyclerView.ViewHolder(binding.root)
}