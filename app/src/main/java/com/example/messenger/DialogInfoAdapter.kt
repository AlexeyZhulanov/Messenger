package com.example.messenger

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.messenger.databinding.ItemFileBinding
import com.example.messenger.databinding.ItemMediaBinding
import com.example.messenger.databinding.ItemMemberBinding
import com.example.messenger.databinding.ItemVoiceBinding
import com.example.messenger.model.MediaItem
import com.example.messenger.model.User
import com.luck.picture.lib.entity.LocalMedia
import com.masoudss.lib.SeekBarOnProgressChanged
import com.masoudss.lib.WaveformSeekBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File

interface DialogActionListener {
    fun onItemClicked(position: Int, localMedias: ArrayList<LocalMedia>)
}

class DialogInfoAdapter(
    private val context: Context,
    private val imageSize: Int,
    private val viewModel: BaseInfoViewModel,
    private val actionListener: DialogActionListener
): RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val mediaItems = mutableListOf<MediaItem>()
    private val localMedias = arrayListOf<LocalMedia>()
    private val durations = mutableListOf<String?>()
    private var currentType: Int? = null
    private val uiScope = CoroutineScope(Dispatchers.Main)

    @SuppressLint("NotifyDataSetChanged")
    private fun setMediaItems(type: Int, items: List<MediaItem>) {
        localMedias.clear()
        mediaItems.clear()
        durations.clear()
        mediaItems.addAll(items)
        if(type == MediaItem.TYPE_MEDIA) {
            items.forEach {
                localMedias.add(viewModel.fileToLocalMedia(File(it.content)))
                viewModel.parseDuration(it.content).let { duration ->
                    durations.add(duration)
                }
            }
        }
        currentType = type
        notifyDataSetChanged()
    }

    fun addMediaItems(type: Int, items: List<MediaItem>) {
        if (items.isNotEmpty() && currentType == type) {
            val startPosition = mediaItems.size
            mediaItems.addAll(items)
            if (type == MediaItem.TYPE_MEDIA) {
                items.forEach {
                    localMedias.add(viewModel.fileToLocalMedia(File(it.content)))
                    viewModel.parseDuration(it.content).let { duration ->
                        durations.add(duration)
                    }
                }
            }
            notifyItemRangeInserted(startPosition, items.size)
        } else {
            setMediaItems(type, items)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return mediaItems[position].type
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            MediaItem.TYPE_MEDIA -> MediaViewHolder(
                ItemMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            MediaItem.TYPE_FILE -> FileViewHolder(
                ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            MediaItem.TYPE_AUDIO -> AudioViewHolder(
                ItemVoiceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            MediaItem.TYPE_USER -> UserViewHolder(
                ItemMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = mediaItems[position]
        when (holder) {
            is MediaViewHolder -> holder.bind(item.content, position)
            is FileViewHolder -> holder.bind(item.content)
            is AudioViewHolder -> holder.bind(item.content)
            is UserViewHolder -> holder.bind(item.user)
        }
    }
    override fun getItemCount(): Int = mediaItems.size

    inner class MediaViewHolder(private val binding: ItemMediaBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(content: String, position: Int) {
            binding.photoImageView.layoutParams.width = imageSize
            binding.photoImageView.layoutParams.height = imageSize
            val file = File(content)
            if (file.exists()) {
                val uri = Uri.fromFile(file)
                Glide.with(context)
                    .load(uri)
                    .placeholder(R.color.app_color_f6)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(binding.photoImageView)

                if(durations[position] != null) {
                    binding.tvDuration.text = durations[position]
                    binding.tvDuration.visibility = View.VISIBLE
                }
                binding.photoImageView.setOnClickListener {
                    actionListener.onItemClicked(position, ArrayList(localMedias))
                }
            } else {
                binding.icError.visibility = View.VISIBLE
            }
        }
    }

    inner class FileViewHolder(private val binding: ItemFileBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(content: String) {
            uiScope.launch {
                val filePathTemp = async {
                    if (viewModel.fManagerIsExist(content)) {
                        return@async Pair(viewModel.fManagerGetFilePath(content), false)
                    } else {
                        try {
                            return@async Pair(viewModel.downloadFile(context, "files", content), true)
                        } catch (e: Exception) {
                            return@async Pair(null, false)
                        }
                    }
                }
                val (filePath, isNeedSave) = filePathTemp.await()
                if(filePath != null) {
                    val file = File(filePath)
                    if(file.exists()) {
                        if(isNeedSave) {
                            viewModel.fManagerSaveFile(content, file.readBytes())
                            viewModel.addTempFile(content)
                        }
                        binding.fileNameTextView.text = file.name
                        binding.fileSizeTextView.text = viewModel.formatFileSize(file.length())
                        binding.fileButton.setOnClickListener {
                            try {
                                val uri: Uri = FileProvider.getUriForFile(context, context.applicationContext.packageName + ".provider", file)

                                val intent = Intent(Intent.ACTION_VIEW)
                                intent.setDataAndType(uri, context.contentResolver.getType(uri))
                                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

                                val chooser = Intent.createChooser(intent, "Выберите приложение для открытия файла")
                                context.startActivity(chooser)
                            } catch (e: IllegalArgumentException) {
                                e.printStackTrace()
                            }
                        }
                    } else {
                        binding.errorImageView.visibility = View.VISIBLE
                    }
                } else {
                    binding.errorImageView.visibility = View.VISIBLE
                }
            }
        }
    }

    inner class AudioViewHolder(private val binding: ItemVoiceBinding) : RecyclerView.ViewHolder(binding.root) {
        private var isPlaying: Boolean = false
        private val handler = Handler(Looper.getMainLooper())

        fun bind(content: String) {
            uiScope.launch {
                val filePathTemp = async {
                    if (viewModel.fManagerIsExist(content)) {
                        return@async Pair(viewModel.fManagerGetFilePath(content), false)
                    } else {
                        try {
                            return@async Pair(viewModel.downloadFile(context, "audio", content), true)
                        } catch (e: Exception) {
                            return@async Pair(null, false)
                        }
                    }
                }
                val (filePath, isNeedSave) = filePathTemp.await()
                if (filePath != null) {
                    val file = File(filePath)
                    if (file.exists()) {
                        if (isNeedSave) {
                            viewModel.fManagerSaveFile(content, file.readBytes())
                            viewModel.addTempFile(content)
                        }
                        val mediaPlayer = MediaPlayer().apply {
                            setDataSource(filePath)
                            prepare()
                        }
                        val duration = mediaPlayer.duration
                        binding.waveformSeekBar.setSampleFrom(file)
                        binding.waveformSeekBar.maxProgress = duration.toFloat()
                        binding.timeVoiceTextView.text = viewModel.formatTime(duration.toLong())

                        val updateSeekBarRunnable = object : Runnable {
                            override fun run() {
                                if (isPlaying && mediaPlayer.isPlaying) {
                                    val currentPosition = mediaPlayer.currentPosition.toFloat()
                                    binding.waveformSeekBar.progress = currentPosition
                                    binding.timeVoiceTextView.text = viewModel.formatTime(currentPosition.toLong())
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
                        binding.waveformSeekBar.onProgressChanged = object :
                            SeekBarOnProgressChanged {
                            override fun onProgressChanged(waveformSeekBar: WaveformSeekBar, progress: Float, fromUser: Boolean) {
                                if (fromUser) {
                                    mediaPlayer.seekTo(progress.toInt())
                                    binding.timeVoiceTextView.text = viewModel.formatTime(progress.toLong())
                                }
                            }
                        }
                        mediaPlayer.setOnCompletionListener {
                            binding.playButton.setImageResource(R.drawable.ic_play)
                            binding.waveformSeekBar.progress = 0f
                            binding.timeVoiceTextView.text = viewModel.formatTime(duration.toLong())
                            isPlaying = false
                            handler.removeCallbacks(updateSeekBarRunnable)
                        }
                    } else {
                        binding.errorImageView.visibility = View.VISIBLE
                        binding.playButton.visibility = View.GONE
                    }
                } else {
                    binding.errorImageView.visibility = View.VISIBLE
                    binding.playButton.visibility = View.GONE
                }
            }
        }
    }

    inner class UserViewHolder(private val binding: ItemMemberBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: User?) {
            if(user != null) {
                uiScope.launch {
                    binding.userNameTextView.text = user.username
                    binding.lastSessionTextView.text = viewModel.formatUserSessionDate(user.lastSession)
                    val avatar = user.avatar ?: ""
                    if (avatar != "") {
                        val filePathTemp = async {
                            if (viewModel.fManagerIsExistAvatar(avatar)) {
                                return@async Pair(viewModel.fManagerGetAvatarPath(avatar), true)
                            } else {
                                try {
                                    return@async Pair(viewModel.downloadAvatar(context, avatar), false)
                                } catch (e: Exception) {
                                    return@async Pair(null, true)
                                }
                            }
                        }
                        val (first, second) = filePathTemp.await()
                        if (first != null) {
                            val file = File(first)
                            if (file.exists()) {
                                if (!second) viewModel.fManagerSaveAvatar(avatar, file.readBytes())
                                val uri = Uri.fromFile(file)
                                binding.photoImageView.imageTintList = null
                                Glide.with(context)
                                    .load(uri)
                                    .apply(RequestOptions.circleCropTransform())
                                    .placeholder(R.color.app_color_f6)
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .into(binding.photoImageView)
                            }
                        }
                    }
                    binding.icDeleteImageView.setOnClickListener {
                        // todo delete user from group and get callback true/false with Toast
                    }
                }
            }
        }
    }
}