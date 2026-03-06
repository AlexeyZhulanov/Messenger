package com.example.messenger

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.messenger.databinding.ItemNewsBinding
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.example.messenger.customview.AdaptiveGridSpacingItemDecoration
import com.example.messenger.customview.CustomLayoutManager
import com.example.messenger.states.AudioPlaybackState
import com.example.messenger.states.FilesState
import com.example.messenger.states.ImagesState
import com.example.messenger.states.NewsAttachmentsState
import com.example.messenger.states.NewsUi
import com.example.messenger.states.VoicesState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow


interface NewsActionListener {
    fun onEditItem(ui: NewsUi)
    fun onDeleteItem(newsId: Int)
    fun onImagesClick(ui: NewsUi, position: Int)
    fun onFileClick(filePath: String)
    fun onPlayVoiceClick(filePath: String)
    fun onVoiceSeek(filePath: String, progress: Int)
}

class NewsDiffCallback : DiffUtil.ItemCallback<NewsUi>() {
    override fun areItemsTheSame(oldItem: NewsUi, newItem: NewsUi): Boolean {
        return oldItem.news.id == newItem.news.id
    }

    override fun areContentsTheSame(oldItem: NewsUi, newItem: NewsUi): Boolean {
        return oldItem == newItem
    }
}

class NewsAdapter(
    private val actionListener: NewsActionListener,
    private val context: Context,
    private var permission: Int,
    private val audioPlaybackState: StateFlow<AudioPlaybackState>,
    private val scope: CoroutineScope
) : PagingDataAdapter<NewsUi, NewsAdapter.NewsViewHolder>(NewsDiffCallback()) {

    private val imagesViewPool = RecyclerView.RecycledViewPool()
    private val filesViewPool = RecyclerView.RecycledViewPool()
    private val voicesViewPool = RecyclerView.RecycledViewPool()

    private var attachmentsMap: Map<Int, NewsAttachmentsState> = emptyMap()

    fun updateAttachmentsState(newMap: Map<Int, NewsAttachmentsState>) {
        attachmentsMap = newMap
        notifyItemRangeChanged(0, itemCount, "ATTACHMENTS_UPDATE")
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setPermission(newPermission: Int) {
        if(permission != 1) {
            permission = newPermission
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
        val binding = ItemNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NewsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int, payloads: List<Any?>) {
        if (payloads.contains("ATTACHMENTS_UPDATE")) {
            val ui = getItem(position) ?: return
            val attachState = attachmentsMap[ui.news.id]
            holder.bindAttachmentsOnly(ui, attachState)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        val ui = getItem(position) ?: return
        val attachState = attachmentsMap[ui.news.id]
        holder.bind(ui, attachState)
    }

    inner class NewsViewHolder(private val binding: ItemNewsBinding) : RecyclerView.ViewHolder(binding.root) {

        private var filesView: View? = null
        private var voiceView: View? = null
        private var photosView: View? = null

        private var uiSave: NewsUi? = null

        private val adapterImages = ImagesAdapter(object: ImagesActionListener {
            override fun onImageClicked(position: Int) {
                uiSave?.let { actionListener.onImagesClick(it, position) }
            }
            override fun onLongImageClicked() {}
        })

        private val adapterFiles = FilesAdapter(object: FilesActionListener {
            override fun onFileOpenClicked(filePath: String) {
                actionListener.onFileClick(filePath)
            }
        })

        private val adapterVoices = VoicesAdapter(audioPlaybackState, scope, object: VoicesActionListener {
            override fun onPlayClick(filePath: String) {
                actionListener.onPlayVoiceClick(filePath)
            }
            override fun onVoiceSeek(filePath: String, progress: Int) {
                actionListener.onVoiceSeek(filePath, progress)
            }
        })

        fun bindAttachmentsOnly(ui: NewsUi, attachState: NewsAttachmentsState?) {
            val imagesState = attachState?.imagesState ?: ui.imagesState
            val filesState = attachState?.filesState ?: ui.filesState
            val voicesState = attachState?.voicesState ?: ui.voicesState

            // Обработка фото (ViewStub)
            when(val state = imagesState) {
                is ImagesState.Loading -> {}
                is ImagesState.Ready -> {
                    if(binding.nestedLayout.isGone) {
                        binding.nestedLayout.visibility = View.VISIBLE
                    }
                    if (photosView == null) {
                        photosView = LayoutInflater.from(context)
                            .inflate(R.layout.viewstub_photos, binding.nestedLayout, true)
                    }
                    photosView?.visibility = View.VISIBLE
                    val photosRecyclerView = photosView?.findViewById<RecyclerView>(R.id.rvPhotos)
                    photosRecyclerView?.setRecycledViewPool(imagesViewPool)
                    photosRecyclerView?.layoutManager = CustomLayoutManager()
                    photosRecyclerView?.addItemDecoration(AdaptiveGridSpacingItemDecoration(2, true))
                    photosRecyclerView?.adapter = adapterImages
                    adapterImages.submitList(state.imageItems)
                }
                is ImagesState.Error -> photosView?.visibility = View.GONE
                null -> photosView?.visibility = View.GONE
            }

            // Обработка файлов (ViewStub)
            when(val state = filesState) {
                is FilesState.Loading -> {}
                is FilesState.Ready -> {
                    if(binding.nestedLayout.isGone) {
                        binding.nestedLayout.visibility = View.VISIBLE
                    }
                    if (filesView == null) {
                        filesView = LayoutInflater.from(context)
                            .inflate(R.layout.viewstub_files, binding.nestedLayout, true)
                    }
                    filesView?.visibility = View.VISIBLE
                    val filesRecyclerView = filesView?.findViewById<RecyclerView>(R.id.rvFiles)
                    filesRecyclerView?.setRecycledViewPool(filesViewPool)
                    filesRecyclerView?.layoutManager = LinearLayoutManager(context)
                    filesRecyclerView?.adapter = adapterFiles
                    adapterFiles.submitList(state.items)
                }
                is FilesState.Error -> filesView?.visibility = View.GONE
                null -> filesView?.visibility = View.GONE
            }

            // Обработка голосовых (ViewStub)
            when(val state = voicesState) {
                is VoicesState.Loading -> {}
                is VoicesState.Ready -> {
                    if(binding.nestedLayout.isGone) {
                        binding.nestedLayout.visibility = View.VISIBLE
                    }
                    if (voiceView == null) {
                        voiceView = LayoutInflater.from(context)
                            .inflate(R.layout.viewstub_voice, binding.nestedLayout, true)
                    }
                    voiceView?.visibility = View.VISIBLE
                    val voiceRecyclerView = voiceView?.findViewById<RecyclerView>(R.id.rvVoices)
                    voiceRecyclerView?.setRecycledViewPool(voicesViewPool)
                    voiceRecyclerView?.layoutManager = LinearLayoutManager(context)
                    voiceRecyclerView?.adapter = adapterVoices
                    adapterVoices.submitList(state.items)
                }
                is VoicesState.Error -> voiceView?.visibility = View.GONE
                null -> voiceView?.visibility = View.GONE
            }

            if(filesView == null && voiceView == null && photosView == null) {
                binding.nestedLayout.visibility = View.GONE
            }
        }

        fun bind(ui: NewsUi, attachState: NewsAttachmentsState?) {
            uiSave = ui
            if(ui.news.text != null && ui.news.text != "") {
                binding.textContainer.visibility = View.VISIBLE
                binding.textContainer.text = ui.news.text
            } else binding.textContainer.visibility = View.GONE
            val txt = ui.news.headerText
            binding.headerTextView.text = if(txt.isNullOrEmpty()) "Amessenger" else txt
            binding.editedText.isVisible = ui.news.isEdited
            binding.dateText.text = ui.formattedDate
            val strViewsCount = ui.news.viewsCount.toString()
            binding.viewCountTextView.text = strViewsCount

            bindAttachmentsOnly(ui, attachState)

            if(permission == 1) {
                binding.root.setOnClickListener { showPopupMenu(itemView, ui) }
            }
        }
    }

    private fun showPopupMenu(itemView: View, ui: NewsUi) {
        val popupMenu = PopupMenu(context, itemView)
        popupMenu.menuInflater.inflate(R.menu.popup_menu_news, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.item_edit -> {
                    actionListener.onEditItem(ui)
                    true
                }
                R.id.item_delete -> {
                    actionListener.onDeleteItem(ui.news.id)
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }
}