package com.example.messenger

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.messenger.databinding.ItemFileBinding
import com.example.messenger.states.FileItem

interface FilesActionListener {
    fun onFileOpenClicked(filePath: String)
}

object FileDiffCallback : DiffUtil.ItemCallback<FileItem>() {
    override fun areItemsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
        return oldItem.localPath == newItem.localPath
    }
    override fun areContentsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
        return oldItem == newItem
    }
}

class FilesAdapter(
    private val actionListener: FilesActionListener
) : ListAdapter<FileItem, FilesAdapter.FilesViewHolder>(FileDiffCallback) {

    override fun getItemCount(): Int = currentList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilesViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemFileBinding.inflate(inflater, parent, false)
        return FilesViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FilesViewHolder, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            fileNameTextView.text = item.fileName
            fileSizeTextView.text = item.fileSize
            fileButton.setOnClickListener {
                actionListener.onFileOpenClicked(item.localPath)
            }
        }
    }

    class FilesViewHolder(val binding: ItemFileBinding) : RecyclerView.ViewHolder(binding.root)
}