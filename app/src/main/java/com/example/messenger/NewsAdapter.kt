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
import com.example.messenger.MessageAdapter.AdaptiveGridSpacingItemDecoration
import com.example.messenger.MessageAdapter.CustomLayoutManager
import com.example.messenger.databinding.ItemNewsBinding
import com.example.messenger.model.News
import com.luck.picture.lib.entity.LocalMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File


interface NewsActionListener {
    fun onEditItem(news: News, triple: Triple<ArrayList<LocalMedia>, List<File>, List<File>>)
    fun onDeleteItem(newsId: Int)
    fun onImagesClick(images: ArrayList<LocalMedia>, position: Int)
    fun onFileClick(file: File)
}

class NewsDiffCallback : DiffUtil.ItemCallback<News>() {
    override fun areItemsTheSame(oldItem: News, newItem: News): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: News, newItem: News): Boolean {
        return oldItem == newItem
    }
}

class NewsAdapter(
    private val actionListener: NewsActionListener,
    private val context: Context,
    private val newsViewModel: NewsViewModel,
    private var permission: Int
) : PagingDataAdapter<News, NewsAdapter.NewsViewHolder>(NewsDiffCallback()) {

    private val uiScopeMain = CoroutineScope(Dispatchers.Main)

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

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        val news = getItem(position) ?: return
        val isInLast10 = position >= itemCount - 10
        holder.bind(news, isInLast10)
    }

    inner class NewsViewHolder(private val binding: ItemNewsBinding) : RecyclerView.ViewHolder(binding.root) {

        private var filesView: View? = null
        private var voiceView: View? = null
        private var photosView: View? = null

        private val adapterImages = ImagesAdapter(context, object: ImagesActionListener {
            override fun onImageClicked(images: ArrayList<LocalMedia>, position: Int) {
                actionListener.onImagesClick(images, position)
            }

            override fun onLongImageClicked(position: Int) {}
        })

        private val adapterFiles = FilesAdapter(newsViewModel, object: FilesActionListener {
            override fun onFileOpenClicked(file: File) {
                actionListener.onFileClick(file)
            }
        })

        private val adapterVoices = VoicesAdapter(newsViewModel)

        private val listVoiceFiles: MutableList<File> = mutableListOf()

        fun bind(news: News, isInLast10: Boolean) {
            if(news.text != null && news.text != "") {
                binding.textContainer.visibility = View.VISIBLE
                binding.textContainer.text = news.text
            } else binding.textContainer.visibility = View.GONE
            binding.headerTextView.text = news.headerText
            binding.editedText.visibility = if(news.isEdited) View.VISIBLE else View.GONE
            binding.dateText.text = newsViewModel.formatMessageNews(news.timestamp)
            val strViewsCount = news.viewsCount.toString()
            binding.viewCountTextView.text = strViewsCount
            // Обработка фото (ViewStub)
            if (news.images?.isNotEmpty() == true) {
                if (photosView == null) {
                    photosView = LayoutInflater.from(context)
                        .inflate(R.layout.viewstub_photos, binding.nestedLayout, true)
                }
                photosView?.visibility = View.VISIBLE
                val photosRecyclerView = photosView?.findViewById<RecyclerView>(R.id.rvPhotos)
                photosRecyclerView?.layoutManager = CustomLayoutManager()
                photosRecyclerView?.addItemDecoration(AdaptiveGridSpacingItemDecoration(2, true))
                photosRecyclerView?.adapter = adapterImages
                uiScopeMain.launch {
                    val semaphore = Semaphore(4) // default photos value
                    val localMedias = async {
                        val medias = arrayListOf<LocalMedia>()
                        news.images?.forEach { photo ->
                            val filePath = async {
                                semaphore.withPermit {
                                    if (newsViewModel.fManagerIsExistNews(photo)) {
                                        Pair(newsViewModel.fManagerGetFilePathNews(photo), true)
                                    } else {
                                        try {
                                            Pair(newsViewModel.downloadNews(context, photo), false)
                                        } catch (e: Exception) {
                                            Pair(null, true)
                                        }
                                    }
                                }
                            }
                            val (first, second) = filePath.await()
                            if (first != null) {
                                val file = File(first)
                                if (file.exists()) {
                                    if (!second && isInLast10) newsViewModel.fManagerSaveFileNews(photo, file.readBytes())
                                    medias += newsViewModel.fileToLocalMedia(file)
                                }
                            }
                        }
                        return@async medias
                    }
                    adapterImages.images = localMedias.await()
                }
            } else photosView?.visibility = View.GONE

            // Обработка файлов (ViewStub)
            if (news.files?.isNotEmpty() == true) {
                if (filesView == null) {
                    filesView = LayoutInflater.from(context)
                        .inflate(R.layout.viewstub_files, binding.nestedLayout, true)
                }
                filesView?.visibility = View.VISIBLE
                val filesRecyclerView = filesView?.findViewById<RecyclerView>(R.id.rvFiles)
                filesRecyclerView?.layoutManager = LinearLayoutManager(context)
                filesRecyclerView?.adapter = adapterFiles
                uiScopeMain.launch {
                    val semaphore = Semaphore(3) // 3 because files can be big
                    val files = async {
                        val filesTemp = mutableListOf<File>()
                        news.files?.forEach {
                            val filePathTemp = async {
                                semaphore.withPermit {
                                    if (newsViewModel.fManagerIsExistNews(it)) {
                                        Pair(newsViewModel.fManagerGetFilePathNews(it), true)
                                    } else {
                                        try {
                                            Pair(newsViewModel.downloadNews(context, it), false)
                                        } catch (e: Exception) {
                                            Pair(null, true)
                                        }
                                    }
                                }
                            }
                            val (first, second) = filePathTemp.await()
                            if (first != null) {
                                val file = File(first)
                                if (file.exists()) {
                                    if (!second && isInLast10) newsViewModel.fManagerSaveFileNews(it, file.readBytes())
                                    filesTemp += file
                                }
                            }
                        }
                        return@async filesTemp
                    }
                    adapterFiles.files = files.await()
                }

            } else filesView?.visibility = View.GONE

            // Обработка голосовых (ViewStub)
            if (news.voices?.isNotEmpty() == true) {
                if (voiceView == null) {
                    voiceView = LayoutInflater.from(context)
                        .inflate(R.layout.viewstub_voice, binding.nestedLayout, true)
                }
                voiceView?.visibility = View.VISIBLE
                val voiceRecyclerView = voiceView?.findViewById<RecyclerView>(R.id.rvVoices)
                voiceRecyclerView?.layoutManager = LinearLayoutManager(context)
                voiceRecyclerView?.adapter = adapterVoices
                uiScopeMain.launch {
                    val semaphore = Semaphore(5) // 5 because all voices is light weight
                    val audioPaths = async {
                        val audiosTemp = mutableListOf<String>()
                        news.voices?.forEach {
                            val filePathTemp = async {
                                semaphore.withPermit {
                                    if (newsViewModel.fManagerIsExistNews(it)) {
                                        Pair(newsViewModel.fManagerGetFilePathNews(it), true)
                                    } else {
                                        try {
                                            Pair(newsViewModel.downloadNews(context, it), false)
                                        } catch (e: Exception) {
                                            Pair(null, true)
                                        }
                                    }
                                }
                            }
                            val (first, second) = filePathTemp.await()
                            if (first != null) {
                                val file = File(first)
                                if (file.exists()) {
                                    if (!second && isInLast10) newsViewModel.fManagerSaveFileNews(it, file.readBytes())
                                    audiosTemp += first
                                    listVoiceFiles.add(file)
                                }
                            }
                        }
                        return@async audiosTemp
                    }
                    adapterVoices.voicePaths = audioPaths.await()
                }

            } else voiceView?.visibility = View.GONE

            if(permission == 1) {
                binding.icOptions.visibility = View.VISIBLE
                binding.icOptions.setOnClickListener {
                    val triple = Triple(adapterImages.images, adapterFiles.files, listVoiceFiles.toList())
                    showPopupMenu(itemView, news, triple)
                }
            }
        }
    }

    private fun showPopupMenu(itemView: View, news: News, triple: Triple<ArrayList<LocalMedia>, List<File>, List<File>>) {
        val popupMenu = PopupMenu(context, itemView)
        popupMenu.menuInflater.inflate(R.menu.popup_menu_news, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.item_edit -> {
                    actionListener.onEditItem(news, triple)
                    true
                }
                R.id.item_delete -> {
                    actionListener.onDeleteItem(news.id)
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }
}