package com.example.messenger

import android.content.Context
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.messenger.model.News


interface NewsActionListener {
    // todo
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
    private val newsViewModel: NewsViewModel
) : PagingDataAdapter<News, RecyclerView.ViewHolder>(NewsDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        TODO("Not yet implemented")
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        TODO("Not yet implemented")
    }

//    fun bind(news: NewsItem) {
//        // Обработка фото
//        if (news.photos.isNotEmpty()) {
//            photosFlexbox.visibility = View.VISIBLE
//            photosFlexbox.removeAllViews() // Очищаем, чтобы избежать дублирования
//
//            news.photos.forEach { photo ->
//                val imageView = ImageView(photosFlexbox.context).apply {
//                    layoutParams = FlexboxLayout.LayoutParams(
//                        FlexboxLayout.LayoutParams.WRAP_CONTENT,
//                        FlexboxLayout.LayoutParams.WRAP_CONTENT
//                    ).apply {
//                        flexBasisPercent = 0.3f // До 3 фото в строку
//                        marginEnd = 8
//                        bottomMargin = 8
//                    }
//                    scaleType = ImageView.ScaleType.CENTER_CROP
//                    Glide.with(context).load(photo).into(this)
//                }
//                photosFlexbox.addView(imageView)
//            }
//        } else {
//            photosFlexbox.visibility = View.GONE
//        }
//
//        // Обработка файлов (ViewStub)
//        if (news.files.isNotEmpty()) {
//            if (filesView == null) filesView = filesStub.inflate()
//            filesView?.visibility = View.VISIBLE
//            val filesRecyclerView = filesView?.findViewById<RecyclerView>(R.id.filesRecyclerView)
//            filesRecyclerView?.adapter = FilesAdapter(news.files)
//        } else {
//            filesView?.visibility = View.GONE
//        }
//
//        // Обработка голосовых (ViewStub)
//        if (news.voiceMessages.isNotEmpty()) {
//            if (voiceView == null) voiceView = voiceStub.inflate()
//            voiceView?.visibility = View.VISIBLE
//            val voiceRecyclerView = voiceView?.findViewById<RecyclerView>(R.id.voiceRecyclerView)
//            voiceRecyclerView?.adapter = VoiceAdapter(news.voiceMessages)
//        } else {
//            voiceView?.visibility = View.GONE
//        }
//    }
}