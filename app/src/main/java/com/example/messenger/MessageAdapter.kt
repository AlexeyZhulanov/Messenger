package com.example.messenger

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.messenger.databinding.ItemFileReceiverBinding
import com.example.messenger.databinding.ItemFileSenderBinding
import com.example.messenger.databinding.ItemMessageReceiverBinding
import com.example.messenger.databinding.ItemMessageSenderBinding
import com.example.messenger.databinding.ItemTextImageReceiverBinding
import com.example.messenger.databinding.ItemTextImageSenderBinding
import com.example.messenger.databinding.ItemTextImagesReceiverBinding
import com.example.messenger.databinding.ItemTextImagesSenderBinding
import com.example.messenger.databinding.ItemVoiceReceiverBinding
import com.example.messenger.databinding.ItemVoiceSenderBinding
import com.example.messenger.model.ConversationSettings
import com.example.messenger.model.Message
import com.example.messenger.model.RetrofitService
import com.example.messenger.picker.DateUtils
import com.luck.picture.lib.config.PictureMimeType
import com.luck.picture.lib.config.SelectMimeType
import com.luck.picture.lib.entity.LocalMedia
import com.masoudss.lib.SeekBarOnProgressChanged
import com.masoudss.lib.WaveformSeekBar
import com.masoudss.lib.utils.WaveGravity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

interface MessageActionListener {
    fun onMessageClick(message: Message, itemView: View)
    fun onMessageClickImage(message: Message, itemView: View, localMedias: ArrayList<LocalMedia>)
    fun onMessageLongClick(itemView: View)
    fun onImagesClick(images: ArrayList<LocalMedia>, position: Int)
}

class MessageAdapter(
    private val actionListener: MessageActionListener,
    private val otherUserId: Int,
    private val context: Context
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var messages: Map<Message, String> = emptyMap()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            val dates = mutableSetOf<String>()
            val newMessages = mutableMapOf<Message, String>()
            for((message, _) in value) {
                val date = formatMessageDate(message.timestamp)
                if(date !in dates) {
                    dates.add(date)
                    newMessages[message] = date
                } else {
                    newMessages[message] = ""
                }
            }
            field = newMessages
            Log.d("testAdapterSet", "$field")
            notifyDataSetChanged()
        }

    var canLongClick: Boolean = true
    private var checkedPositions: MutableSet<Int> = mutableSetOf()
    private var checkedFiles: MutableMap<String, String> = mutableMapOf()
    lateinit var dialogSettings: ConversationSettings
    private val retrofitService: RetrofitService
        get() = Singletons.retrofitRepository as RetrofitService
    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.IO + job)
    private val uiScopeMain = CoroutineScope(Dispatchers.Main + job)

    fun getDeleteList(): Pair<List<Int>, Map<String, String>> {
        val list = mutableListOf<Int>()
        checkedPositions.forEach { list.add(messages.keys.elementAt(it).id) }
        return Pair(list, checkedFiles)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearPositions() {
        canLongClick = true
        checkedPositions.clear()
        checkedFiles.clear()
    }

    private fun savePosition(message: Message) {
        val position = messages.keys.indexOf(message)
        if (position in checkedPositions) {
            checkedPositions.remove(position)
        } else {
            checkedPositions.add(position)
        }
        notifyItemChanged(position)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun onLongClick(message: Message) {
        if(canLongClick) {
            savePosition(message)
            canLongClick = false
            notifyDataSetChanged()
        }
    }

    private fun savePositionFile(message: Message, filePath: String, fileType: String) {
        val position = messages.keys.indexOf(message)
        if (position in checkedPositions) {
            checkedPositions.remove(position)
            checkedFiles.remove(filePath)
        } else {
            checkedPositions.add(position)
            checkedFiles[filePath] = fileType
        }
        notifyItemChanged(position)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun onLongClickFile(message: Message, filePath: String, fileType: String) {
        if (canLongClick) {
            savePositionFile(message, filePath, fileType)
            canLongClick = false
            notifyDataSetChanged()
        }
    }

    private fun savePositionFiles(message: Message, filePaths: Map<String, String>) {
        val position = messages.keys.indexOf(message)
        if (position in checkedPositions) {
            checkedPositions.remove(position)
            filePaths.forEach { checkedFiles.remove(it.key) }
        } else {
            checkedPositions.add(position)
            filePaths.forEach { checkedFiles[it.key] = it.value }
        }
        notifyItemChanged(position)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun onLongClickFiles(message: Message, filePaths: Map<String, String>) {
        if (canLongClick) {
            savePositionFiles(message, filePaths)
            canLongClick = false
            notifyDataSetChanged()
        }
    }

    companion object {
        private const val TYPE_TEXT_RECEIVER = 0
        private const val TYPE_TEXT_SENDER = 1
        private const val TYPE_VOICE_RECEIVER = 2
        private const val TYPE_VOICE_SENDER = 3
        private const val TYPE_FILE_RECEIVER = 4
        private const val TYPE_FILE_SENDER = 5
        private const val TYPE_TEXT_IMAGE_RECEIVER = 6
        private const val TYPE_TEXT_IMAGE_SENDER = 7
        private const val TYPE_TEXT_IMAGES_RECEIVER = 8
        private const val TYPE_TEXT_IMAGES_SENDER = 9
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages.keys.elementAt(position)
        if(message.idSender == otherUserId) {
            return when {
                message.images?.isNotEmpty() == true -> {
                    when {
                        message.images?.size == 1 -> TYPE_TEXT_IMAGE_RECEIVER
                        else -> TYPE_TEXT_IMAGES_RECEIVER
                    }
                }
                message.text?.isNotEmpty() == true -> TYPE_TEXT_RECEIVER
                else -> {
                    when {
                        message.voice?.isNotEmpty() == true -> TYPE_VOICE_RECEIVER
                        else -> TYPE_FILE_RECEIVER
                    }
                }
            }
        } else {
            return when {
                message.images?.isNotEmpty() == true -> {
                    when {
                        message.images?.size == 1 -> TYPE_TEXT_IMAGE_SENDER
                        else -> TYPE_TEXT_IMAGES_SENDER
                    }
                }
                message.text?.isNotEmpty() == true -> TYPE_TEXT_SENDER
                else -> {
                    when {
                        message.voice?.isNotEmpty() == true -> TYPE_VOICE_SENDER
                        else -> TYPE_FILE_SENDER
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_TEXT_RECEIVER -> MessagesViewHolderReceiver(
                ItemMessageReceiverBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            TYPE_TEXT_SENDER -> MessagesViewHolderSender(
                ItemMessageSenderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            TYPE_VOICE_RECEIVER -> MessagesViewHolderVoiceReceiver(
                ItemVoiceReceiverBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            TYPE_VOICE_SENDER -> MessagesViewHolderVoiceSender(
                ItemVoiceSenderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            TYPE_FILE_RECEIVER -> MessagesViewHolderFileReceiver(
                ItemFileReceiverBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            TYPE_FILE_SENDER -> MessagesViewHolderFileSender(
                ItemFileSenderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            TYPE_TEXT_IMAGE_RECEIVER -> MessagesViewHolderTextImageReceiver(
                ItemTextImageReceiverBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            TYPE_TEXT_IMAGE_SENDER -> MessagesViewHolderTextImageSender(
                ItemTextImageSenderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            TYPE_TEXT_IMAGES_RECEIVER -> MessagesViewHolderTextImagesReceiver(
                ItemTextImagesReceiverBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            TYPE_TEXT_IMAGES_SENDER -> MessagesViewHolderTextImagesSender(
                ItemTextImagesSenderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages.keys.elementAt(position)
        var flagText = false
        if(!message.text.isNullOrEmpty()) flagText = true
        when (holder) {
            is MessagesViewHolderReceiver -> holder.bind(message, position)
            is MessagesViewHolderSender -> holder.bind(message, position)
            is MessagesViewHolderVoiceReceiver -> holder.bind(message, position)
            is MessagesViewHolderVoiceSender -> holder.bind(message, position)
            is MessagesViewHolderFileReceiver -> holder.bind(message, position)
            is MessagesViewHolderFileSender -> holder.bind(message, position)
            is MessagesViewHolderTextImageReceiver -> holder.bind(message, position, flagText)
            is MessagesViewHolderTextImageSender -> holder.bind(message, position, flagText)
            is MessagesViewHolderTextImagesReceiver -> holder.bind(message, position, flagText)
            is MessagesViewHolderTextImagesSender -> holder.bind(message, position, flagText)
        }
    }

    override fun getItemCount(): Int = messages.size

    // ViewHolder для текстовых сообщений получателя
    inner class MessagesViewHolderReceiver(private val binding: ItemMessageReceiverBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message, position: Int) {
            binding.messageReceiverTextView.text = message.text
            val time = formatMessageTime(message.timestamp)
            val date = messages.values.elementAt(position)
            if(date != "") {
                binding.dateTextView.visibility = View.VISIBLE
                binding.dateTextView.text = date
            } else binding.dateTextView.visibility = View.GONE
            binding.timeTextView.text = time
            if(!canLongClick && dialogSettings.canDelete) {
                if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = position in checkedPositions
                binding.checkbox.setOnClickListener {
                    savePosition(message)
                }
            }
            else { binding.checkbox.visibility = View.GONE }
            if (message.isRead) {
                binding.icCheck.visibility = View.INVISIBLE
                binding.icCheck2.visibility = View.VISIBLE
            }
            if(message.isEdited) binding.editTextView.visibility = View.VISIBLE
            binding.root.setOnClickListener {
                if(!canLongClick) {
                    savePosition(message)
                }
                else
                    actionListener.onMessageClick(message, itemView)
            }
            binding.root.setOnLongClickListener {
                if(canLongClick) {
                    onLongClick(message)
                    actionListener.onMessageLongClick(itemView)
                }
                true
            }
        }
    }

    // ViewHolder для текстовых сообщений отправителя
    inner class MessagesViewHolderSender(private val binding: ItemMessageSenderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message, position: Int) {
            binding.messageSenderTextView.text = message.text
            val time = formatMessageTime(message.timestamp)
            val date = messages.values.elementAt(position)
            if(date != "") {
                binding.dateTextView.visibility = View.VISIBLE
                binding.dateTextView.text = date
            } else binding.dateTextView.visibility = View.GONE
            binding.timeTextView.text = time
            if(!canLongClick) {
                if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = position in checkedPositions
                binding.checkbox.setOnClickListener {
                    savePosition(message)
                }
            }
            else { binding.checkbox.visibility = View.GONE }
            if (message.isRead) {
                binding.icCheck.visibility = View.INVISIBLE
                binding.icCheck2.visibility = View.VISIBLE
            }
            if(message.isEdited) binding.editTextView.visibility = View.VISIBLE
            binding.root.setOnClickListener {
                if(!canLongClick) {
                    savePosition(message)
                }
                else
                    actionListener.onMessageClick(message, itemView)
            }
            binding.root.setOnLongClickListener {
                if(canLongClick) {
                    onLongClick(message)
                    actionListener.onMessageLongClick(itemView)
                }
                true
            }
        }
    }

    private fun fileToLocalMedia(file: File): LocalMedia {
        val localMedia = LocalMedia()

        // Установите путь файла
        localMedia.path = file.absolutePath

        // Определите MIME тип файла на основе его расширения
        localMedia.mimeType = when (file.extension.lowercase(Locale.ROOT)) {
            "jpg", "jpeg" -> PictureMimeType.ofJPEG()
            "png" -> PictureMimeType.ofPNG()
            "mp4" -> PictureMimeType.ofMP4()
            "avi" -> PictureMimeType.ofAVI()
            "gif" -> PictureMimeType.ofGIF()
            else -> PictureMimeType.MIME_TYPE_AUDIO // Или другой тип по умолчанию
        }

        // Установите дополнительные свойства
        localMedia.isCompressed = false // Или true, если вы хотите сжать изображение
        localMedia.isCut = false // Если это изображение было обрезано
        localMedia.isOriginal = false // Если это оригинальный файл

        if (localMedia.mimeType == PictureMimeType.MIME_TYPE_VIDEO) {
            // Получаем длительность видео
            val duration = getVideoDuration(file)
            localMedia.duration = duration
        } else {
            localMedia.duration = 0 // Для изображений длительность обычно равна 0
        }

        return localMedia
    }

    private fun getVideoDuration(file: File): Long {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            return durationStr?.toLongOrNull() ?: 0
        } catch (e: Exception) {
            e.printStackTrace()
            return 0
        } finally {
            retriever.release()
        }
    }

    class CustomLayoutManager : RecyclerView.LayoutManager() {

        private var columnWidth = 0
        private var rowHeight = 0

        override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
            detachAndScrapAttachedViews(recycler)

            if (itemCount == 0) return

            val columns = when {
                itemCount <= 2 -> 2
                itemCount == 3 -> 2
                itemCount == 4 -> 2
                itemCount in 5..6 -> 3
                else -> 3
            }

            val rows = when {
                itemCount <= 2 -> 1
                itemCount == 3 -> 2
                itemCount == 4 -> 2
                itemCount in 5..6 -> 2
                itemCount in 7..9 -> 3
                else -> 4
            }

            columnWidth = width / columns
            rowHeight = height / rows

            var leftOffset = 0
            var topOffset = 0
            var currentColumn = 0

            if (itemCount % 2 != 0) {

                for (i in 0 until itemCount) {
                    val view = recycler.getViewForPosition(i)
                    val spanSize = if (itemCount % 2 == 0 && i == itemCount - 1) 2 else if (i == 0 && itemCount > 1) 2 else 1
                    val itemWidth = columnWidth * spanSize
                    val widthSpec = View.MeasureSpec.makeMeasureSpec(itemWidth, View.MeasureSpec.EXACTLY)
                    val heightSpec = View.MeasureSpec.makeMeasureSpec(rowHeight, View.MeasureSpec.EXACTLY)
                    view.measure(widthSpec, heightSpec)
                    addView(view)

                    val outRect = Rect()
                    calculateItemDecorationsForChild(view, outRect)

                    if ((currentColumn + spanSize) > columns) {
                        currentColumn = 0
                        leftOffset = 0
                        topOffset += rowHeight
                    }

                    layoutDecorated(
                        view,
                        leftOffset + outRect.left,
                        topOffset + outRect.top,
                        leftOffset + itemWidth - outRect.right,
                        topOffset + rowHeight - outRect.bottom
                    )

                    leftOffset += itemWidth
                    currentColumn += spanSize
                }
            } else {
                for (i in 0 until itemCount) {
                val view = recycler.getViewForPosition(i)
                addView(view)
                measureChildWithMargins(view, 0, 0)

                val itemHeight = measuredHeightWithMargins(view)
                val itemWidth = columnWidth

                    val outRect = Rect()
                    calculateItemDecorationsForChild(view, outRect)

                layoutDecorated(
                    view,
                    leftOffset + outRect.left,
                    topOffset + outRect.top,
                    leftOffset + itemWidth - outRect.right,
                    topOffset + itemHeight - outRect.bottom
                )

                leftOffset += itemWidth
                currentColumn++

                if (currentColumn >= columns) {
                    leftOffset = 0
                    topOffset += rowHeight
                    currentColumn = 0
                }
            }
            }
        }

        private fun measuredHeightWithMargins(view: View): Int {
            val lp = view.layoutParams as RecyclerView.LayoutParams
            val height = view.measuredHeight
            val marginTop = lp.topMargin
            val marginBottom = lp.bottomMargin
            return height + marginTop + marginBottom
        }
        override fun measureChildWithMargins(child: View, widthUsed: Int, heightUsed: Int) {
            val widthSpec = View.MeasureSpec.makeMeasureSpec(columnWidth, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(rowHeight, View.MeasureSpec.EXACTLY)
            child.measure(widthSpec, heightSpec)
        }
        override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
            return RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, // Use MATCH_PARENT for width
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    class GridSpacingItemDecoration(private val spanCount: Int, private val spacing: Int, private val includeEdge: Boolean) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val position = (view.layoutParams as RecyclerView.LayoutParams).bindingAdapterPosition
            val column = position % spanCount

            if (includeEdge) {
                outRect.left = spacing - column * spacing / spanCount
                outRect.right = (column + 1) * spacing / spanCount
                if (position < spanCount) {
                    outRect.top = spacing
                }
                outRect.bottom = spacing
            } else {
                outRect.left = column * spacing / spanCount
                outRect.right = spacing - (column + 1) * spacing / spanCount
                if (position >= spanCount) {
                    outRect.top = spacing
                }
            }
        }
    }


    inner class MessagesViewHolderVoiceReceiver(private val binding: ItemVoiceReceiverBinding) : RecyclerView.ViewHolder(binding.root) {
        private var filePath: String = ""
        private var isPlaying: Boolean = false
        private val handler = Handler(Looper.getMainLooper())
        fun bind(message: Message, position: Int) {
            binding.playButton.visibility = View.VISIBLE
            uiScopeMain.launch {
                val filePathTemp = async(Dispatchers.IO) { retrofitService.downloadFile(context, "audio", message.voice!!) }
                val file = File(filePathTemp.await())
                filePath = filePathTemp.await()
                if (file.exists()) {
                    val mediaPlayer = MediaPlayer().apply {
                        setDataSource(filePath)
                        prepare()
                    }
                    val duration = mediaPlayer.duration
                    binding.waveformSeekBar.setSampleFrom(file)
                    binding.waveformSeekBar.maxProgress = duration.toFloat()
                    binding.timeVoiceTextView.text = formatTime(duration.toLong())

                    val updateSeekBarRunnable = object : Runnable {
                        override fun run() {
                            if (isPlaying && mediaPlayer.isPlaying) {
                                val currentPosition = mediaPlayer.currentPosition.toFloat()
                                binding.waveformSeekBar.progress = currentPosition
                                binding.timeVoiceTextView.text = formatTime(currentPosition.toLong())
                                handler.postDelayed(this, 100)
                            }
                        }
                    }
                    binding.playButton.setOnClickListener {
                        if (!isPlaying) {
                            mediaPlayer.start()
                            binding.playButton.setImageResource(R.drawable.ic_pause)
                            isPlaying = true
                            handler.post(updateSeekBarRunnable) // Запуск обновления SeekBar
                        } else {
                            mediaPlayer.pause()
                            binding.playButton.setImageResource(R.drawable.ic_play)
                            isPlaying = false
                            handler.removeCallbacks(updateSeekBarRunnable) // Остановка обновления SeekBar
                        }
                    }
                    // Обработка изменения положения SeekBar
                    binding.waveformSeekBar.onProgressChanged = object : SeekBarOnProgressChanged {
                        override fun onProgressChanged(waveformSeekBar: WaveformSeekBar, progress: Float, fromUser: Boolean) {
                            if (fromUser) {
                                mediaPlayer.seekTo(progress.toInt())
                                binding.timeVoiceTextView.text = formatTime(progress.toLong())
                            }
                        }
                    }
                    mediaPlayer.setOnCompletionListener {
                        binding.playButton.setImageResource(R.drawable.ic_play)
                        binding.waveformSeekBar.progress = 0f
                        binding.timeVoiceTextView.text = formatTime(duration.toLong())
                        isPlaying = false
                        handler.removeCallbacks(updateSeekBarRunnable)
                    }
                } else {
                    Log.e("VoiceError", "File does not exist: $filePath")
                    binding.progressBar.visibility = View.GONE
                    binding.errorImageView.visibility = View.VISIBLE
                    binding.playButton.visibility = View.GONE
                }
            }
            val time = formatMessageTime(message.timestamp)
            val date = messages.values.elementAt(position)
            if(date != "") {
                binding.dateTextView.visibility = View.VISIBLE
                binding.dateTextView.text = date
            } else binding.dateTextView.visibility = View.GONE
            binding.timeTextView.text = time
            if(!canLongClick) {
                if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = position in checkedPositions
                binding.checkbox.setOnClickListener {
                    savePositionFile(message, File(filePath).name, "audio")
                }
            }
            else { binding.checkbox.visibility = View.GONE }
            if (message.isRead) {
                binding.icCheck.visibility = View.INVISIBLE
                binding.icCheck2.visibility = View.VISIBLE
            }
            if(message.isEdited) binding.editTextView.visibility = View.VISIBLE
            binding.root.setOnClickListener {
                if(!canLongClick) {
                    savePositionFile(message, File(filePath).name, "audio")
                }
                else
                    actionListener.onMessageClick(message, itemView)
            }
            binding.root.setOnLongClickListener {
                if(canLongClick) {
                    onLongClickFile(message, File(filePath).name, "audio")
                    actionListener.onMessageLongClick(itemView)
                }
                true
            }
        }
    }

    inner class MessagesViewHolderVoiceSender(private val binding: ItemVoiceSenderBinding) : RecyclerView.ViewHolder(binding.root) {

        private var filePath: String = ""
        private var isPlaying: Boolean = false
        private val handler = Handler(Looper.getMainLooper())
        fun bind(message: Message, position: Int) {
            binding.playButton.visibility = View.VISIBLE
            uiScopeMain.launch {
                val filePathTemp = async(Dispatchers.IO) { retrofitService.downloadFile(context, "audio", message.voice!!) }
                val file = File(filePathTemp.await())
                filePath = filePathTemp.await()
                if (file.exists()) {
                    val mediaPlayer = MediaPlayer().apply {
                        setDataSource(filePath)
                        prepare()
                    }
                    val duration = mediaPlayer.duration
                    binding.waveformSeekBar.setSampleFrom(file)
                    binding.waveformSeekBar.maxProgress = duration.toFloat()
                    binding.timeVoiceTextView.text = formatTime(duration.toLong())

                    val updateSeekBarRunnable = object : Runnable {
                        override fun run() {
                            if (isPlaying && mediaPlayer.isPlaying) {
                                val currentPosition = mediaPlayer.currentPosition.toFloat()
                                binding.waveformSeekBar.progress = currentPosition
                                binding.timeVoiceTextView.text = formatTime(currentPosition.toLong())
                                handler.postDelayed(this, 100)
                            }
                        }
                    }
                    binding.playButton.setOnClickListener {
                        if (!isPlaying) {
                            mediaPlayer.start()
                            binding.playButton.setImageResource(R.drawable.ic_pause)
                            isPlaying = true
                            handler.post(updateSeekBarRunnable) // Запуск обновления SeekBar
                        } else {
                            mediaPlayer.pause()
                            binding.playButton.setImageResource(R.drawable.ic_play)
                            isPlaying = false
                            handler.removeCallbacks(updateSeekBarRunnable) // Остановка обновления SeekBar
                        }
                    }
                    // Обработка изменения положения SeekBar
                    binding.waveformSeekBar.onProgressChanged = object : SeekBarOnProgressChanged {
                        override fun onProgressChanged(waveformSeekBar: WaveformSeekBar, progress: Float, fromUser: Boolean) {
                            if (fromUser) {
                                mediaPlayer.seekTo(progress.toInt())
                                binding.timeVoiceTextView.text = formatTime(progress.toLong())
                            }
                        }
                    }
                    mediaPlayer.setOnCompletionListener {
                        binding.playButton.setImageResource(R.drawable.ic_play)
                        binding.waveformSeekBar.progress = 0f
                        binding.timeVoiceTextView.text = formatTime(duration.toLong())
                        isPlaying = false
                        handler.removeCallbacks(updateSeekBarRunnable)
                    }
                } else {
                        Log.e("VoiceError", "File does not exist: $filePath")
                        binding.progressBar.visibility = View.GONE
                        binding.errorImageView.visibility = View.VISIBLE
                        binding.playButton.visibility = View.GONE
                }
            }
            val time = formatMessageTime(message.timestamp)
            val date = messages.values.elementAt(position)
            if(date != "") {
                binding.dateTextView.visibility = View.VISIBLE
                binding.dateTextView.text = date
            } else binding.dateTextView.visibility = View.GONE
            binding.timeTextView.text = time
            if(!canLongClick) {
                if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = position in checkedPositions
                binding.checkbox.setOnClickListener {
                    savePositionFile(message, File(filePath).name, "audio")
                }
            }
            else { binding.checkbox.visibility = View.GONE }
            if (message.isRead) {
                binding.icCheck.visibility = View.INVISIBLE
                binding.icCheck2.visibility = View.VISIBLE
            }
            if(message.isEdited) binding.editTextView.visibility = View.VISIBLE
            binding.root.setOnClickListener {
                if(!canLongClick) {
                    savePositionFile(message, File(filePath).name, "audio")
                }
                else
                    actionListener.onMessageClick(message, itemView)
            }
            binding.root.setOnLongClickListener {
                if(canLongClick) {
                    onLongClickFile(message, File(filePath).name, "audio")
                    actionListener.onMessageLongClick(itemView)
                }
                true
            }
        }
    }

    @SuppressLint("DefaultLocale")
    private fun formatTime(milliseconds: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    inner class MessagesViewHolderFileReceiver(private val binding: ItemFileReceiverBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message, position: Int) {
            // todo
        }
    }

    inner class MessagesViewHolderFileSender(private val binding: ItemFileSenderBinding) : RecyclerView.ViewHolder(binding.root) {

        private var filePath: String = ""
        fun bind(message: Message, position: Int) {
            uiScope.launch {
                val filePathTemp = async { retrofitService.downloadFile(context, "files", message.file!!) }
                val file = File(filePathTemp.await())
                filePath = filePathTemp.await()
                if (file.exists()) {
                    // todo
                } else {
                    withContext(Dispatchers.Main) {
                        Log.e("FileError", "File does not exist: $filePath")
                        binding.progressBar.visibility = View.GONE
                        binding.errorImageView.visibility = View.VISIBLE
                    }
                }
            }
            val time = formatMessageTime(message.timestamp)
            val date = messages.values.elementAt(position)
            if(date != "") {
                binding.dateTextView.visibility = View.VISIBLE
                binding.dateTextView.text = date
            } else binding.dateTextView.visibility = View.GONE
            binding.timeTextView.text = time
            if(!canLongClick) {
                if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = position in checkedPositions
                binding.checkbox.setOnClickListener {
                    savePositionFile(message, File(filePath).name, "files")
                }
            }
            else { binding.checkbox.visibility = View.GONE }
            if (message.isRead) {
                binding.icCheck.visibility = View.INVISIBLE
                binding.icCheck2.visibility = View.VISIBLE
            }
            if(message.isEdited) binding.editTextView.visibility = View.VISIBLE
            binding.root.setOnClickListener {
                if(!canLongClick) {
                    savePositionFile(message, File(filePath).name, "files")
                }
                else
                    actionListener.onMessageClick(message, itemView)
            }
            binding.root.setOnLongClickListener {
                if(canLongClick) {
                    onLongClickFile(message, File(filePath).name, "files")
                    actionListener.onMessageLongClick(itemView)
                }
                true
            }
        }
    }

    inner class MessagesViewHolderTextImageReceiver(private val binding: ItemTextImageReceiverBinding) : RecyclerView.ViewHolder(binding.root) {

        private var filePath: String = ""
        fun bind(message: Message, position: Int, flagText: Boolean) {
            if(flagText) {
                binding.messageReceiverTextView.visibility = View.VISIBLE
                binding.messageReceiverTextView.text = message.text
            } else {
                binding.messageReceiverTextView.visibility = View.GONE
            }

            uiScope.launch {
                binding.progressBar.visibility = View.VISIBLE
                val filePathTemp = async { retrofitService.downloadFile(context, "photos", message.images!!.first()) }
                val file = File(filePathTemp.await())
                filePath = filePathTemp.await()
                if (file.exists()) {
                    val uri = Uri.fromFile(file)
                    val localMedia = fileToLocalMedia(file)
                    val chooseModel = localMedia.chooseModel
                    binding.tvDuration.visibility = if (PictureMimeType.isHasVideo(localMedia.mimeType)) View.VISIBLE else View.GONE
                    if(chooseModel == SelectMimeType.ofAudio()) {
                        binding.tvDuration.visibility = View.VISIBLE
                        binding.tvDuration.setCompoundDrawablesRelativeWithIntrinsicBounds(com.luck.picture.lib.R.drawable.ps_ic_audio, 0, 0, 0)
                    } else {
                        binding.tvDuration.setCompoundDrawablesRelativeWithIntrinsicBounds(com.luck.picture.lib.R.drawable.ps_ic_video, 0, 0, 0)
                    }
                    binding.tvDuration.text = (DateUtils.formatDurationTime(localMedia.duration))
                    if(chooseModel == SelectMimeType.ofAudio()) {
                        binding.receiverImageView.setImageResource(com.luck.picture.lib.R.drawable.ps_audio_placeholder)
                    } else {
                        Glide.with(context)
                            .load(uri)
                            .centerCrop()
                            .placeholder(R.color.app_color_f6)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(binding.receiverImageView)
                    }
                    binding.progressBar.visibility = View.GONE
                    binding.receiverImageView.setOnClickListener {
                        actionListener.onImagesClick(arrayListOf(localMedia), 0)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Log.e("ImageError", "File does not exist: $filePath")
                        binding.progressBar.visibility = View.GONE
                        binding.errorImageView.visibility = View.VISIBLE
                    }
                }
            }
            val time = formatMessageTime(message.timestamp)
            val date = messages.values.elementAt(position)
            if(date != "") {
                binding.dateTextView.visibility = View.VISIBLE
                binding.dateTextView.text = date
            } else binding.dateTextView.visibility = View.GONE
            binding.timeTextView.text = time
            if(!canLongClick && dialogSettings.canDelete) {
                if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = position in checkedPositions
                binding.checkbox.setOnClickListener {
                    savePositionFile(message, File(filePath).name, "photos")
                }
            }
            else { binding.checkbox.visibility = View.GONE }
            if (message.isRead) {
                binding.icCheck.visibility = View.INVISIBLE
                binding.icCheck2.visibility = View.VISIBLE
            }
            if(message.isEdited) binding.editTextView.visibility = View.VISIBLE
            binding.root.setOnClickListener {
                if(!canLongClick) {
                    savePositionFile(message, File(filePath).name, "photos")
                }
                else
                    actionListener.onMessageClickImage(message, itemView, arrayListOf(fileToLocalMedia(File(filePath))))
            }
            binding.root.setOnLongClickListener {
                if(canLongClick) {
                    onLongClickFile(message, File(filePath).name, "photos")
                    actionListener.onMessageLongClick(itemView)
                }
                true
            }
        }
    }

    inner class MessagesViewHolderTextImageSender(private val binding: ItemTextImageSenderBinding) : RecyclerView.ViewHolder(binding.root) {

        private var filePath: String = ""
        fun bind(message: Message, position: Int, flagText: Boolean) {
            if(flagText) {
                binding.messageSenderTextView.visibility = View.VISIBLE
                binding.messageSenderTextView.text = message.text
            } else {
                binding.messageSenderTextView.visibility = View.GONE
            }
            uiScope.launch {
                binding.progressBar.visibility = View.VISIBLE
                val filePathTemp = async { retrofitService.downloadFile(context, "photos", message.images!!.first()) }
                val file = File(filePathTemp.await())
                filePath = filePathTemp.await()
                if (file.exists()) {
                    val uri = Uri.fromFile(file)
                    val localMedia = fileToLocalMedia(file)
                    val chooseModel = localMedia.chooseModel
                    withContext(Dispatchers.Main) {
                        binding.tvDuration.visibility =
                            if (PictureMimeType.isHasVideo(localMedia.mimeType)) View.VISIBLE else View.GONE
                        if (chooseModel == SelectMimeType.ofAudio()) {
                            binding.tvDuration.visibility = View.VISIBLE
                            binding.tvDuration.setCompoundDrawablesRelativeWithIntrinsicBounds(com.luck.picture.lib.R.drawable.ps_ic_audio, 0, 0, 0)
                        } else {
                            binding.tvDuration.setCompoundDrawablesRelativeWithIntrinsicBounds(com.luck.picture.lib.R.drawable.ps_ic_video, 0, 0, 0)
                        }
                        binding.tvDuration.text = (DateUtils.formatDurationTime(localMedia.duration))
                        if (chooseModel == SelectMimeType.ofAudio()) {
                            binding.senderImageView.setImageResource(com.luck.picture.lib.R.drawable.ps_audio_placeholder)
                        } else {
                            Glide.with(context)
                                .load(uri)
                                .centerCrop()
                                .placeholder(R.color.app_color_f6)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .into(binding.senderImageView)
                        }
                        binding.progressBar.visibility = View.GONE
                        binding.senderImageView.setOnClickListener {
                            actionListener.onImagesClick(arrayListOf(localMedia), 0)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Log.e("ImageError", "File does not exist: $filePath")
                        binding.progressBar.visibility = View.GONE
                        binding.errorImageView.visibility = View.VISIBLE
                    }
                }
            }
            val time = formatMessageTime(message.timestamp)
            val date = messages.values.elementAt(position)
            if(date != "") {
                binding.dateTextView.visibility = View.VISIBLE
                binding.dateTextView.text = date
            } else binding.dateTextView.visibility = View.GONE
            binding.timeTextView.text = time
            if(!canLongClick) {
                if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = position in checkedPositions
                binding.checkbox.setOnClickListener {
                    savePositionFile(message, File(filePath).name, "photos")
                }
            }
            else { binding.checkbox.visibility = View.GONE }
            if (message.isRead) {
                binding.icCheck.visibility = View.INVISIBLE
                binding.icCheck2.visibility = View.VISIBLE
            }
            if(message.isEdited) binding.editTextView.visibility = View.VISIBLE
            binding.root.setOnClickListener {
                if(!canLongClick) {
                    savePositionFile(message, File(filePath).name, "photos")
                }
                else
                    actionListener.onMessageClickImage(message, itemView, arrayListOf(fileToLocalMedia(File(filePath))))
            }
            binding.root.setOnLongClickListener {
                if(canLongClick) {
                    onLongClickFile(message, File(filePath).name, "photos")
                    actionListener.onMessageLongClick(itemView)
                }
                true
            }
        }
    }

    inner class MessagesViewHolderTextImagesReceiver(private val binding: ItemTextImagesReceiverBinding) : RecyclerView.ViewHolder(binding.root) {

        private lateinit var mes: Message

        private val adapter = ImagesAdapter(context, object: ImagesActionListener {
            override fun onImageClicked(images: ArrayList<LocalMedia>, position: Int) {
                actionListener.onImagesClick(images, position)
            }

            override fun onLongImageClicked(images: ArrayList<LocalMedia>, position: Int) {
                if(canLongClick) {
                    onLongClickFiles(mes, filePaths)
                    actionListener.onMessageLongClick(itemView)
                }
            }
        })

        private var filePaths: MutableMap<String, String> = mutableMapOf()
        private var filePathsForClick: List<String> = listOf()
        init {
            binding.recyclerview.layoutManager = CustomLayoutManager()
            binding.recyclerview.addItemDecoration(GridSpacingItemDecoration(3, 2, true))
            binding.recyclerview.adapter = adapter
        }
        fun bind(message: Message, position: Int, flagText: Boolean) {
            filePathsForClick = emptyList()
            mes = message
            if(flagText) {
                binding.messageReceiverTextView.visibility = View.VISIBLE
                binding.messageReceiverTextView.text = message.text
            } else {
                binding.messageReceiverTextView.visibility = View.GONE
            }
            binding.progressBar.visibility = View.VISIBLE
            uiScope.launch {
                val localMedias = async {
                    val medias = arrayListOf<LocalMedia>()
                    for (image in message.images!!) {
                        val filePath =
                            async { retrofitService.downloadFile(context, "photos", image) }
                        val file = File(filePath.await())
                        filePaths[File(filePath.await()).name] = "photos"
                        filePathsForClick += filePath.await()
                        if (file.exists()) {
                            medias += fileToLocalMedia(file)
                        } else {
                            withContext(Dispatchers.Main) {
                                Log.e("ImageError", "File does not exist: $filePath")
                                binding.progressBar.visibility = View.GONE
                                binding.errorImageView.visibility = View.VISIBLE
                            }
                        }
                    }
                    return@async medias
                }
                adapter.images = localMedias.await()
                binding.progressBar.visibility = View.GONE
            }
            val time = formatMessageTime(message.timestamp)
            val date = messages.values.elementAt(position)
            if(date != "") {
                binding.dateTextView.visibility = View.VISIBLE
                binding.dateTextView.text = date
            } else binding.dateTextView.visibility = View.GONE
            binding.timeTextView.text = time
            if(!canLongClick) {
                if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = position in checkedPositions
                binding.checkbox.setOnClickListener {
                    savePositionFiles(message, filePaths)
                }
            }
            else { binding.checkbox.visibility = View.GONE }
            if (message.isRead) {
                binding.icCheck.visibility = View.INVISIBLE
                binding.icCheck2.visibility = View.VISIBLE
            }
            if(message.isEdited) binding.editTextView.visibility = View.VISIBLE
            binding.root.setOnClickListener {
                if(!canLongClick) {
                    savePositionFiles(message, filePaths)
                }
                else {
                    val medias: ArrayList<LocalMedia> = filePathsForClick.map { fileToLocalMedia(File(it)) } as ArrayList<LocalMedia>
                    actionListener.onMessageClickImage(message, itemView, medias)
                }
            }
            binding.root.setOnLongClickListener {
                if(canLongClick) {
                    onLongClickFiles(message, filePaths)
                    actionListener.onMessageLongClick(itemView)
                }
                true
            }
        }
    }

    inner class MessagesViewHolderTextImagesSender(private val binding: ItemTextImagesSenderBinding) : RecyclerView.ViewHolder(binding.root) {

        private lateinit var mes: Message

        private val adapter = ImagesAdapter(context, object: ImagesActionListener {
            override fun onImageClicked(images: ArrayList<LocalMedia>, position: Int) {
                actionListener.onImagesClick(images, position)
            }

            override fun onLongImageClicked(images: ArrayList<LocalMedia>, position: Int) {
                if(canLongClick) {
                    onLongClickFiles(mes, filePaths)
                    actionListener.onMessageLongClick(itemView)
                }
            }
        })

        private var filePaths: MutableMap<String, String> = mutableMapOf()
        private var filePathsForClick: List<String> = listOf()
        init {
            binding.recyclerview.layoutManager = CustomLayoutManager()
            binding.recyclerview.addItemDecoration(GridSpacingItemDecoration(3, 2, true))
            binding.recyclerview.adapter = adapter
        }
        fun bind(message: Message, position: Int, flagText: Boolean) {
            filePathsForClick = emptyList()
            mes = message
            if(flagText) {
                binding.messageSenderTextView.visibility = View.VISIBLE
                binding.messageSenderTextView.text = message.text
            } else {
                binding.messageSenderTextView.visibility = View.GONE
            }
            binding.errorImageView.visibility = View.GONE
            uiScope.launch {
                val localMedias = async {
                    val medias = arrayListOf<LocalMedia>()
                    for (image in message.images!!) {
                        val filePath =
                            async { retrofitService.downloadFile(context, "photos", image) }
                        val file = File(filePath.await())
                        filePaths[File(filePath.await()).name] = "photos"
                        filePathsForClick += filePath.await()
                        if (file.exists()) {
                            medias += fileToLocalMedia(file)
                        } else {
                            withContext(Dispatchers.Main) {
                                Log.e("ImageError", "File does not exist: $filePath")
                                binding.progressBar.visibility = View.GONE
                                binding.errorImageView.visibility = View.VISIBLE
                            }
                        }
                    }
                    return@async medias
                }
                withContext(Dispatchers.Main) {
                    adapter.images = localMedias.await()
                    Log.d("testAdapterImages", "${adapter.images}")
                    binding.progressBar.visibility = View.GONE
                }
            }
            val time = formatMessageTime(message.timestamp)
            val date = messages.values.elementAt(position)
            if(date != "") {
                binding.dateTextView.visibility = View.VISIBLE
                binding.dateTextView.text = date
            } else binding.dateTextView.visibility = View.GONE
            binding.timeTextView.text = time
            if(!canLongClick) {
                if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = position in checkedPositions
                binding.checkbox.setOnClickListener {
                    savePositionFiles(message, filePaths)
                }
            }
            else { binding.checkbox.visibility = View.GONE }
            if (message.isRead) {
                binding.icCheck.visibility = View.INVISIBLE
                binding.icCheck2.visibility = View.VISIBLE
            }
            if(message.isEdited) binding.editTextView.visibility = View.VISIBLE
            binding.root.setOnClickListener {
                if(!canLongClick) {
                    savePositionFiles(message, filePaths)
                }
                else {
                    val medias: ArrayList<LocalMedia> = filePathsForClick.map { fileToLocalMedia(File(it)) } as ArrayList<LocalMedia>
                    actionListener.onMessageClickImage(message, itemView, medias)
                }
            }
            binding.root.setOnLongClickListener {
                if(canLongClick) {
                    onLongClickFiles(message, filePaths)
                    actionListener.onMessageLongClick(itemView)
                }
                true
            }
        }
    }

    private fun formatMessageTime(timestamp: Long?): String {
        if (timestamp == null) return "-"

        // Приведение серверного времени (МСК GMT+3) к GMT
        val greenwichMessageDate = Calendar.getInstance().apply {
            timeInMillis = timestamp - 10800000
        }
        val dateFormatToday = SimpleDateFormat("HH:mm", Locale.getDefault())
        return dateFormatToday.format(greenwichMessageDate.time)
    }

    private fun formatMessageDate(timestamp: Long?): String {
        if (timestamp == null) return ""

        // Приведение серверного времени (МСК GMT+3) к GMT
        val greenwichMessageDate = Calendar.getInstance().apply {
            timeInMillis = timestamp - 10800000
        }
        val dateFormatMonthDay = SimpleDateFormat("d MMMM", Locale.getDefault())
        val dateFormatYear = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
        val localNow = Calendar.getInstance()

        return when {
            isToday(localNow, greenwichMessageDate) -> dateFormatMonthDay.format(greenwichMessageDate.time)
            isThisYear(localNow, greenwichMessageDate) -> dateFormatMonthDay.format(greenwichMessageDate.time)
            else -> dateFormatYear.format(greenwichMessageDate.time)
        }
    }

    private fun isToday(now: Calendar, messageDate: Calendar): Boolean {
        return now.get(Calendar.YEAR) == messageDate.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == messageDate.get(Calendar.DAY_OF_YEAR)
    }

    private fun isThisYear(now: Calendar, messageDate: Calendar): Boolean {
        return now.get(Calendar.YEAR) == messageDate.get(Calendar.YEAR)
    }
}
