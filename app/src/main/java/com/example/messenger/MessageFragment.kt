package com.example.messenger

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.messenger.databinding.FragmentMessageBinding
import com.example.messenger.model.ConversationSettings
import com.example.messenger.model.Dialog
import com.example.messenger.model.Message
import com.example.messenger.model.User
import com.example.messenger.picker.ExoPlayerEngine
import com.example.messenger.picker.FilePickerManager
import com.example.messenger.picker.GlideEngine
import com.example.messenger.voicerecorder.AudioConverter
import com.example.messenger.voicerecorder.AudioRecorder
import com.luck.picture.lib.basic.PictureSelector
import com.luck.picture.lib.config.InjectResourceSource
import com.luck.picture.lib.entity.LocalMedia
import com.luck.picture.lib.interfaces.OnExternalPreviewEventListener
import com.luck.picture.lib.interfaces.OnInjectLayoutResourceListener
import com.tougee.recorderview.AudioRecordView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.PrintWriter
import kotlin.coroutines.cancellation.CancellationException

@AndroidEntryPoint
class MessageFragment(
    private val dialog: Dialog,
    private val currentUser: User
) : Fragment(), AudioRecordView.Callback {
    private lateinit var binding: FragmentMessageBinding
    private lateinit var adapter: MessageAdapter
    private lateinit var imageAdapter: ImageAdapter
    private lateinit var preferences: SharedPreferences
    private lateinit var pickFileLauncher: ActivityResultLauncher<Array<String>>
    private var audioRecord: AudioRecorder? = null
    private var lastSessionString: String = ""
    private var editFlag = false
    private var answerFlag = false
    private var answerMessage: Pair<Int, String>? = null
    private var typingStoppedTimeout = 3000L // delay 3 seconds
    private var typingHandler: Handler = Handler(Looper.getMainLooper())
    private var isTyping = false
    private var isStopPagination = false
    private val typingRunnable = Runnable {
        if (isTyping) {
            viewModel.sendTypingEvent(false)
            isTyping = false
        }
    }
    private val viewModel: MessageViewModel by viewModels()

    private val file: File by lazy {
        val f = File("${requireContext().externalCacheDir?.absolutePath}${File.separator}audio.pcm")
        if (!f.exists()) {
            f.createNewFile()
        }
        f
    }

    private val tmpFile: File by lazy {
        val f = File("${requireContext().externalCacheDir?.absolutePath}${File.separator}tmp.pcm")
        if (!f.exists()) {
            f.createNewFile()
        }
        f
    }

    private val adapterDataObserver = object : RecyclerView.AdapterDataObserver() {
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            binding.recyclerview.scrollToPosition(0)
            binding.recyclerview.adapter?.unregisterAdapterDataObserver(this)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pickFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) {uri: Uri? ->
            uri?.let {
                handleFileUri(uri) // обработка выбранного файла
            }
        }
    }

    @OptIn(FlowPreview::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.setDialogInfo(dialog.id, dialog.otherUser.id)
        lifecycleScope.launch {
            launch {
                viewModel.pagingDataFlow.collectLatest { pagingData ->
                    if(pagingData.isNotEmpty()) {
                        Log.d("testPagingFlow", "Submitting paging data")
                        if(viewModel.isFirstPage()) {
                            val mes = viewModel.getUnsentMessages()
                            val summaryPagingData = if(mes != null) {
                                val pair = mes.map { Pair(it, "") }
                                 pair + pagingData
                            } else pagingData
                            adapter.submitList(summaryPagingData)
                        }
                        else {
                            val updatedList = adapter.currentList.toMutableList()
                            updatedList.addAll(pagingData)
                            adapter.submitList(updatedList)
                        }
                    } else {
                        isStopPagination = true
                    }
                }
            }
            launch {
                viewModel.newMessageFlow.collectLatest { newMessage ->
                    if (newMessage != null) {
                        viewModel.updateLastDate(newMessage.first.timestamp)
                        adapter.addNewMessage(newMessage)
                    }
                }
            }
            launch {
                viewModel.unsentMessageFlow.collectLatest { uMessage ->
                    if(uMessage != null) {
                        Log.d("testUnsentFlow", "OK")
                        adapter.addNewMessage(Pair(uMessage, ""))
                    }
                }
            }
            launch {
                viewModel.editMessageFlow.collectLatest { message ->
                    if(message != null) {
                        Log.d("testEditFlow", "OK")
                        val updatedList = adapter.currentList.toMutableList()
                        val index = updatedList.indexOfFirst { it.first.id == message.id }
                        if(index != -1) {
                            val oldPair = updatedList[index]
                            updatedList[index] = oldPair.copy(first = message)
                            adapter.submitList(updatedList)
                        }
                    }
                }
            }
        }
        val firstItem = adapter.currentList.firstOrNull()
        if(firstItem != null) viewModel.updateLastDate(firstItem.first.timestamp)
        lifecycleScope.launch {
            val lastReadMessageId = viewModel.getLastMessageId()
            if(lastReadMessageId != -1) {
                val position = findPositionById(lastReadMessageId)
                if(position != -1) {
                    binding.recyclerview.scrollToPosition(position)
                } else {
                    val pos = viewModel.getPreviousMessageId(lastReadMessageId)
                    if(pos != -1) binding.recyclerview.scrollToPosition(pos)
                }
            }
        }
        viewModel.setMarkScrollListener(binding.recyclerview, adapter)
        val toolbarContainer: FrameLayout = view.findViewById(R.id.toolbar_container)
        val defaultToolbar = LayoutInflater.from(context)
            .inflate(R.layout.custom_action_bar, toolbarContainer, false)
        toolbarContainer.addView(defaultToolbar)
        val backArrow: ImageView = view.findViewById(R.id.back_arrow)
        backArrow.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        val profilePhoto: ImageView = view.findViewById(R.id.photoImageView)
        profilePhoto.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(
                    R.id.fragmentContainer,
                    DialogInfoFragment(dialog, lastSessionString, viewModel),
                    "DIALOG_INFO_FRAGMENT_TAG"
                )
                .addToBackStack(null)
                .commit()
        }
        val icVolumeOff: ImageView = view.findViewById(R.id.ic_volume_off)
        lifecycleScope.launch {
            icVolumeOff.visibility = if(viewModel.isNotificationsEnabled(dialog.id)) View.GONE else View.VISIBLE
            val avatar = dialog.otherUser.avatar ?: ""
            if (avatar != "") {
                val filePathTemp = async {
                    if (viewModel.fManagerIsExistAvatar(avatar)) {
                        return@async Pair(viewModel.fManagerGetAvatarPath(avatar), true)
                    } else {
                        try {
                            return@async Pair(viewModel.downloadAvatar(requireContext(), avatar), false)
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
                        profilePhoto.imageTintList = null
                        Glide.with(requireContext())
                            .load(uri)
                            .apply(RequestOptions.circleCropTransform())
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(profilePhoto)
                    }
                }
            }
        }
        val userName: TextView = view.findViewById(R.id.userNameTextView)
        userName.text = dialog.otherUser.username
        userName.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(
                    R.id.fragmentContainer,
                    DialogInfoFragment(dialog, lastSessionString, viewModel),
                    "DIALOG_INFO_FRAGMENT_TAG2"
                )
                .addToBackStack(null)
                .commit()
        }
        val lastSession: TextView = view.findViewById(R.id.lastSessionTextView)
        val typingTextView: TextView = view.findViewById(R.id.typingText)
        val dot1: View = view.findViewById(R.id.dot1)
        val dot2: View = view.findViewById(R.id.dot2)
        val dot3: View = view.findViewById(R.id.dot3)
        val typingAnimation = TypingAnimation(dot1, dot2, dot3)
        lifecycleScope.launch {
            viewModel.typingState
                .debounce(2000)
                .distinctUntilChanged()
                .collect { isTyping ->
                if (isTyping) {
                    lastSession.visibility = View.INVISIBLE
                    typingTextView.visibility = View.VISIBLE
                    dot1.visibility = View.VISIBLE
                    dot2.visibility = View.VISIBLE
                    dot3.visibility = View.VISIBLE
                    typingAnimation.startAnimation()
                } else {
                    typingAnimation.stopAnimation()
                    typingTextView.visibility = View.INVISIBLE
                    dot1.visibility = View.INVISIBLE
                    dot2.visibility = View.INVISIBLE
                    dot3.visibility = View.INVISIBLE
                    lastSession.visibility = View.VISIBLE
                }
            }
        }
        viewModel.fetchLastSession()
        viewModel.lastSessionString.observe(viewLifecycleOwner) { sessionString ->
            lastSession.text = sessionString
            lastSessionString = sessionString
        }
        lifecycleScope.launch {
            viewModel.readMessagesFlow.collect { readMessagesIds ->
                adapter.updateMessagesAsRead(readMessagesIds)
            }
        }
        val options: ImageView = view.findViewById(R.id.ic_options)
        options.setOnClickListener {
            showPopupMenu(it, R.menu.popup_menu_dialog)
        }
        lifecycleScope.launch {
            viewModel.deleteState.collectLatest {
                if(it == 1) {
                    Toast.makeText(requireContext(), "Все сообщения были удалены", Toast.LENGTH_SHORT).show()
                }
                if(it == 2) {
                    Toast.makeText(requireContext(), "Диалог был удален", Toast.LENGTH_SHORT).show()
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        }
    }

    @SuppressLint("InflateParams", "NotifyDataSetChanged", "DiscouragedApi")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMessageBinding.inflate(inflater, container, false)
        preferences = requireContext().getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE)
        val wallpaper = preferences.getString(PREF_WALLPAPER, "")
        if (wallpaper != "") {
            val resId = resources.getIdentifier(wallpaper, "drawable", requireContext().packageName)
            if (resId != 0)
                binding.messageLayout.background =
                    ContextCompat.getDrawable(requireContext(), resId)
        }
        val filePickerManager = FilePickerManager(this, null, null)
            adapter = MessageAdapter(object : MessageActionListener {
                override fun onUnsentMessageClick(message: Message, itemView: View) {
                    showPopupMenuUnsent(itemView, R.menu.popup_menu_unsent, message)
                }

                override fun onUnsentMessagesAdd() {
                    lifecycleScope.launch {
                        delay(200)
                        binding.recyclerview.smoothScrollToPosition(0)
                    }
                }

                override fun onMessageClick(message: Message, itemView: View, isSender: Boolean) {
                    showPopupMenuMessage(itemView, R.menu.popup_menu_message, message, null, isSender)
                }

                override fun onMessageClickImage(message: Message, itemView: View, localMedias: ArrayList<LocalMedia>, isSender: Boolean) {
                    showPopupMenuMessage(itemView, R.menu.popup_menu_message, message, localMedias, isSender)
                }

                override fun onMessageLongClick(itemView: View) {
                    var flag = true
                    lifecycleScope.launch {
                        viewModel.stopRefresh()
                        binding.floatingActionButtonDelete.visibility = View.VISIBLE
                        binding.floatingActionButtonForward.visibility = View.VISIBLE
                    requireActivity().onBackPressedDispatcher.addCallback(
                        viewLifecycleOwner,
                        object : OnBackPressedCallback(true) {
                            @SuppressLint("NotifyDataSetChanged")
                            override fun handleOnBackPressed() {
                                if (!adapter.canLongClick && flag) {
                                    adapter.clearPositions()
                                    binding.floatingActionButtonDelete.visibility = View.GONE
                                    binding.floatingActionButtonForward.visibility = View.GONE
                                } else {
                                    //Removing this callback
                                    remove()
                                    requireActivity().onBackPressedDispatcher.onBackPressed()
                                }
                                viewModel.startRefresh()
                            }
                        })
                    binding.floatingActionButtonDelete.setOnClickListener {
                       val messagesToDelete = adapter.getDeleteList()
                        if (messagesToDelete.isNotEmpty()) {
                            lifecycleScope.launch {
                                binding.progressBar.visibility = View.VISIBLE
                                binding.floatingActionButtonDelete.visibility = View.GONE
                                binding.floatingActionButtonForward.visibility = View.GONE
                                val response = async { viewModel.deleteMessages(messagesToDelete) }
                                if(response.await()) {
                                    adapter.clearPositions()
                                    viewModel.startRefresh()
                                    binding.progressBar.visibility = View.GONE
                                } else {
                                    adapter.clearPositions()
                                    viewModel.startRefresh()
                                    binding.progressBar.visibility = View.GONE
                                    Toast.makeText(requireContext(), "Не удалось удалить сообщения", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                        binding.floatingActionButtonForward.setOnClickListener {
                            flag = false
                            val fMessages = adapter.getForwardList()
                            fMessages.sortedBy { it.first.timestamp }
                            val (messages, booleans) = fMessages.unzip()
                         if(fMessages.isNotEmpty()) {
                             Log.d("testForwardItems", fMessages.toString())
                             val strings = mutableListOf<String>()
                             booleans.forEach {
                                 if(it) strings.add(currentUser.username)
                                 else strings.add(dialog.otherUser.username)
                             }
                             val bundle = Bundle().apply {
                                 putParcelableArrayList("forwardedMessages", ArrayList(messages))
                                 putStringArrayList("forwardedUsernames", ArrayList(strings))
                             }
                             parentFragmentManager.setFragmentResult("forwardMessagesRequestKey", bundle)
                             requireActivity().onBackPressedDispatcher.onBackPressed()
                         }
                        }
                    }
                }
                override fun onImagesClick(images: ArrayList<LocalMedia>, position: Int) {
                    Log.d("testClickImages", "images: $images")
                    PictureSelector.create(requireActivity())
                        .openPreview()
                        .setImageEngine(GlideEngine.createGlideEngine())
                        .setVideoPlayerEngine(ExoPlayerEngine())
                        .isAutoVideoPlay(false)
                        .isLoopAutoVideoPlay(false)
                        .isVideoPauseResumePlay(true)
                        .setSelectorUIStyle(filePickerManager.selectorStyle)
                        .isPreviewFullScreenMode(true)
                        .setInjectLayoutResourceListener(object: OnInjectLayoutResourceListener {
                            override fun getLayoutResourceId(context: Context?, resourceSource: Int): Int {
                                @Suppress("DEPRECATED_IDENTITY_EQUALS")
                                return if (resourceSource === InjectResourceSource.PREVIEW_LAYOUT_RESOURCE
                                ) R.layout.ps_custom_fragment_preview
                                else InjectResourceSource.DEFAULT_LAYOUT_RESOURCE
                            }
                        })
                        .startActivityPreview(position, false, images)
                }
            }, dialog.otherUser.id, requireContext(), viewModel)
            lifecycleScope.launch {
                try {
                    adapter.dialogSettings = viewModel.getDialogSettings(dialog.id)
                } catch (e: Exception) {
                    adapter.dialogSettings = ConversationSettings()
                }

            }
        imageAdapter = ImageAdapter(requireContext(), object: ImageActionListener {
            override fun onImageClicked(image: LocalMedia, position: Int) {
                Log.d("testClick", "Image clicked: $image")
                PictureSelector.create(requireActivity())
                    .openPreview()
                    .setImageEngine(GlideEngine.createGlideEngine())
                    .setVideoPlayerEngine(ExoPlayerEngine())
                    .isAutoVideoPlay(false)
                    .isLoopAutoVideoPlay(false)
                    .isVideoPauseResumePlay(true)
                    .setSelectorUIStyle(filePickerManager.selectorStyle)
                    .isPreviewFullScreenMode(true)
                    .setExternalPreviewEventListener(MyExternalPreviewEventListener(imageAdapter))
                    .setInjectLayoutResourceListener(object: OnInjectLayoutResourceListener {
                        override fun getLayoutResourceId(context: Context?, resourceSource: Int): Int {
                            @Suppress("DEPRECATED_IDENTITY_EQUALS")
                            return if (resourceSource === InjectResourceSource.PREVIEW_LAYOUT_RESOURCE
                            ) R.layout.ps_custom_fragment_preview
                            else InjectResourceSource.DEFAULT_LAYOUT_RESOURCE
                        }
                    })
                    .startActivityPreview(position, true, imageAdapter.getData())
            }

            override fun onDeleteImage(position: Int) {
                imageAdapter.delete(position)
            }
        })
        binding.enterMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.isNullOrEmpty()) {
                    binding.recordView.visibility = View.VISIBLE
                    if (!editFlag) {
                        binding.enterButton.visibility = View.INVISIBLE
                    } else {
                        binding.editButton.visibility = View.GONE
                    }

                    // Отправляем событие "typing_stopped"
                    viewModel.sendTypingEvent(false)
                    isTyping = false
                    typingHandler.removeCallbacks(typingRunnable)
                } else {
                    binding.recordView.visibility = View.INVISIBLE
                    if (!editFlag) {
                        binding.enterButton.visibility = View.VISIBLE
                    } else {
                        binding.editButton.visibility = View.VISIBLE
                    }

                    if (!isTyping) {
                        // Отправляем событие "typing_started"
                        viewModel.sendTypingEvent(true)
                        isTyping = true
                    }

                    // Перезапускаем таймер отсчета до отправки "typing_stopped"
                    typingHandler.removeCallbacks(typingRunnable)
                    typingHandler.postDelayed(typingRunnable, typingStoppedTimeout)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.recordView.apply {
            activity = requireActivity()
            callback = this@MessageFragment
        }
        binding.attachButton.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val res = async { filePickerManager.openFilePicker(isCircle = false, isFreeStyleCrop = false, imageAdapter.getData()) }
                    imageAdapter.images = res.await()
                    if(res.await().isNotEmpty()) {
                        binding.recordView.visibility = View.INVISIBLE
                        if(!editFlag) binding.enterButton.visibility = View.VISIBLE
                    }
                } catch (e: CancellationException) {
                    Toast.makeText(requireContext(), "Выходим...", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Неизвестная ошибка", Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.attachButton.setOnLongClickListener {
            ChoosePickFragment(object: ChoosePickListener {
                override fun onGalleryClick() {
                    lifecycleScope.launch {
                        try {
                            val res = async { filePickerManager.openFilePicker(isCircle = false, isFreeStyleCrop = true, imageAdapter.getData()) }
                            imageAdapter.images = res.await()
                            if(res.await().isNotEmpty()) {
                                binding.recordView.visibility = View.INVISIBLE
                                binding.enterButton.visibility = View.VISIBLE
                            }
                        } catch (e: CancellationException) {
                            Toast.makeText(requireContext(), "Выходим...", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "Неизвестная ошибка", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                override fun onFileClick() {
                    pickFileLauncher.launch(arrayOf("*/*"))
                }
            }).show(childFragmentManager, "ChoosePickTag")
            true
        }
        binding.emojiButton.setOnClickListener {
            if(binding.emojiPicker.visibility == View.VISIBLE) binding.emojiPicker.visibility = View.GONE
            else {
                val enterText: EditText = requireView().findViewById(R.id.enter_message)
                binding.emojiPicker.visibility = View.VISIBLE
                binding.emojiPicker.setOnEmojiPickedListener { emojicon ->
                    val emoji = emojicon.emoji
                    val start = enterText.selectionStart
                    val end = enterText.selectionEnd
                    enterText.text.replace(start.coerceAtLeast(0), end.coerceAtLeast(0), emoji)
                }
            }
        }
        val layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = false
            reverseLayout = true
        }
        binding.recyclerview.layoutManager = layoutManager
        binding.recyclerview.adapter = adapter
        binding.recyclerview.addItemDecoration(VerticalSpaceItemDecoration(15))
        viewModel.setRecyclerView(binding.recyclerview)
        binding.selectedPhotosRecyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.selectedPhotosRecyclerView.adapter = imageAdapter
        binding.recyclerview.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private val handler = Handler(Looper.getMainLooper())
            private var isWaiting = false
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val layoutManagerInside = recyclerView.layoutManager as LinearLayoutManager
                val lastVisiblePosition = layoutManagerInside.findLastVisibleItemPosition()
                val totalItemCount = layoutManagerInside.itemCount
                if (!isStopPagination && !isWaiting &&
                    lastVisiblePosition >= totalItemCount - 3 && totalItemCount >= 30) {
                    isWaiting = true
                    viewModel.loadNextPage()
                    handler.postDelayed({ isWaiting = false }, 3000)
                }
            }
        })
        binding.enterButton.setOnClickListener {
            val text = binding.enterMessage.text.toString()
            val items = imageAdapter.getData()

            lifecycleScope.launch {
                if (items.isNotEmpty()) {
                    val listik = async {
                        val list = mutableListOf<String>()
                        var flag = true
                        val localFilePaths = mutableListOf<String>()
                        for (item1 in items) {
                            if (item1.duration > 0) {
                                val file = getFileFromContentUri(requireContext(), Uri.parse(item1.availablePath)) ?: continue
                                val tmp = async(Dispatchers.IO) { viewModel.uploadPhoto(file) }
                                val (path, f) = tmp.await()
                                list += path
                                flag = f
                                if(!f) break
                            } else {
                                val tmp = async(Dispatchers.IO) { viewModel.uploadPhoto(File(item1.availablePath)) }
                                val (path, f) = tmp.await()
                                list += path
                                flag = f
                                if(!f) break
                            }
                        }
                         if (!flag) {
                            list.clear()
                            for (item1 in items) {
                                try {
                                    val file = if (item1.duration > 0) {
                                        getFileFromContentUri(requireContext(), Uri.parse(item1.availablePath)) ?: continue
                                    } else {
                                        File(item1.availablePath)
                                    }
                                    val fileName = file.name
                                    val fileBytes = file.readBytes()
                                    viewModel.fManagerSaveFileUnsent(fileName, fileBytes)
                                    localFilePaths.add(viewModel.fManagerGetFilePathUnsent(fileName))
                                    list.add(fileName)
                                } catch (fileException: Exception) {
                                    fileException.printStackTrace()
                                }
                            }
                        }
                    return@async Pair(list, localFilePaths)
                    }
                    val (list, localFilePaths) = listik.await()
                    if(text.isNotEmpty()) {
                        if (list.isNotEmpty()) {
                            if(!answerFlag) viewModel.sendMessage(text, list, null, null, null, false, null, localFilePaths)
                            else {
                                viewModel.sendMessage(text, list, null, null, answerMessage?.first, false, answerMessage?.second, localFilePaths)
                                disableAnswer()
                            }
                        } else {
                            if(!answerFlag) viewModel.sendMessage(text, null, null, null, null, false, null, null)
                            else {
                                viewModel.sendMessage(text, null, null, null, answerMessage?.first, false, answerMessage?.second, null)
                                disableAnswer()
                            }
                        }
                    } else if (list.isNotEmpty()) {
                        if(!answerFlag) viewModel.sendMessage(null, list, null, null, null, false, null, localFilePaths)
                        else {
                            viewModel.sendMessage(null, list, null, null, answerMessage?.first, false, answerMessage?.second, localFilePaths)
                            disableAnswer()
                        }
                    }
                    else Toast.makeText(requireContext(), "Ошибка отправки изображений", Toast.LENGTH_SHORT).show()
                    imageAdapter.clearImages()
            } else {
                    if(text.isNotEmpty()) {
                        if(!answerFlag) viewModel.sendMessage(text, null, null, null, null, false, null, null)
                        else {
                            viewModel.sendMessage(text, null, null, null, answerMessage?.first, false, answerMessage?.second, null)
                            disableAnswer()
                        }
                    }
                }
                try {
                    binding.recyclerview.adapter?.registerAdapterDataObserver(adapterDataObserver)
                } catch (e: Exception) {
                    binding.recyclerview.adapter?.unregisterAdapterDataObserver(adapterDataObserver)
                    binding.recyclerview.adapter?.registerAdapterDataObserver(adapterDataObserver)
                }

                val enterText: EditText = requireView().findViewById(R.id.enter_message)
                enterText.setText("")
                // viewModel.refresh()
            }
        }
        return binding.root
    }

    private fun findPositionById(messageId: Int): Int {
        val currentPagingData = adapter.currentList
        val positionInPagingData = currentPagingData.indexOfFirst { it.first.id == messageId }

        if (positionInPagingData != -1) {
            return positionInPagingData
        }
        return -1
    }

    override val defaultViewModelCreationExtras: CreationExtras
        get() = super.defaultViewModelCreationExtras

    private fun getFileFromContentUri(context: Context, contentUri: Uri): File? {
        val projection = arrayOf(MediaStore.Video.Media.DATA)
        val cursor = context.contentResolver.query(contentUri, projection, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val filePath = it.getString(columnIndex)
                return File(filePath)
            }
        }
        return null
    }

    override fun isReady(): Boolean = true

    override fun onRecordCancel() {
        audioRecord?.stop()
    }

    override fun onRecordEnd() {
        audioRecord?.stop()
        tmpFile.copyTo(file, true)

        val fileOgg = File("${requireContext().externalCacheDir?.absolutePath}${File.separator}audio.ogg")
        if(fileOgg.exists()) fileOgg.delete()
        val converter = AudioConverter()
        converter.convertPcmToOgg(file.path, fileOgg.path) {success, _ ->
            if(success) {
                lifecycleScope.launch {
                    val response = async {
                        val (path, f) = viewModel.uploadAudio(fileOgg)
                        if(f) {
                            return@async Pair(path, null)
                        } else {
                            val fileName = fileOgg.name
                            val fileBytes = fileOgg.readBytes()
                            viewModel.fManagerSaveFileUnsent(fileName, fileBytes)
                            return@async Pair(fileName, listOf(viewModel.fManagerGetFilePathUnsent(fileName)))
                        }
                    }
                    val(first, second) = response.await()
                    if(!answerFlag) viewModel.sendMessage(null, null, first, null, null, false, null, second)
                    else {
                        viewModel.sendMessage(null, null, first, null, answerMessage?.first, false, answerMessage?.second, second)
                        disableAnswer()
                    }
                    try {
                        binding.recyclerview.adapter?.registerAdapterDataObserver(adapterDataObserver)
                    } catch (e: Exception) {
                        binding.recyclerview.adapter?.unregisterAdapterDataObserver(adapterDataObserver)
                        binding.recyclerview.adapter?.registerAdapterDataObserver(adapterDataObserver)
                    }
                //viewModel.refresh()
                }
            } else {
                Log.d("testConvert", "Not OK")
            }
        }
    }

    override fun onRecordStart() {
        clearFile(tmpFile)

        audioRecord = AudioRecorder(ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_WRITE))
        audioRecord?.start(this)
    }

    private fun clearFile(f: File) {
        PrintWriter(f).run {
            print("")
            close()
        }
    }

    override fun onDestroyView() {
        viewModel.stopRefresh()
        super.onDestroyView()
    }

    override fun onPause() {
        viewModel.stopRefresh()
        super.onPause()
        // Получаем последнее видимое сообщение
        val layoutManager = binding.recyclerview.layoutManager as LinearLayoutManager
        val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
        val firstMessageId = if (firstVisibleItemPosition != RecyclerView.NO_POSITION && firstVisibleItemPosition < 25) {
            val firstMessage = adapter.getItemNotProtected(firstVisibleItemPosition).first
            if(firstMessage.isRead) {
                adapter.getItemNotProtected(0).first.id
            } else {
                firstMessage.id
            }
        } else {
            adapter.getItemNotProtected(0).first.id
        }
        viewModel.saveLastMessage(firstMessageId)
    }

    private fun handleFileUri(uri: Uri) {
        val file = uriToFile(uri, requireContext())
        if(file != null) {
            lifecycleScope.launch {
                val response = async {
                    val (path, f) = viewModel.uploadFile(file)
                    if(f) {
                        return@async Pair(path, null)
                    } else {
                        val fileName = file.name
                        val fileBytes = file.readBytes()
                        viewModel.fManagerSaveFileUnsent(fileName, fileBytes)
                        return@async Pair(fileName, listOf(viewModel.fManagerGetFilePathUnsent(fileName)))
                    }
                }
                val(first, second) = response.await()
                if(!answerFlag) viewModel.sendMessage(null, null, null, first, null, false, null, second)
                else {
                    viewModel.sendMessage(null, null, file.name, first, answerMessage?.first, false, answerMessage?.second, second)
                    disableAnswer()
                }
                try {
                    binding.recyclerview.adapter?.registerAdapterDataObserver(adapterDataObserver)
                } catch (e: Exception) {
                    binding.recyclerview.adapter?.unregisterAdapterDataObserver(adapterDataObserver)
                    binding.recyclerview.adapter?.registerAdapterDataObserver(adapterDataObserver)
                }
                // viewModel.refresh()
            }
        } else Toast.makeText(requireContext(), "Что-то не так с файлом", Toast.LENGTH_SHORT).show()
    }

    private fun uriToFile(uri: Uri, context: Context): File? {
        val fileName = getFileName(uri, context)
        // Создаем временный файл в кэше приложения
        val tempFile = File(context.cacheDir, fileName)

        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val outputStream = FileOutputStream(tempFile)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            return tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun getFileName(uri: Uri, context: Context): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    result = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result ?: "temp_file"
    }

    private fun showPopupMenu(view: View, menuRes: Int) {
        val popupMenu = PopupMenu(requireContext(), view)
        popupMenu.menuInflater.inflate(menuRes, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.item_search -> {
                    val toolbarContainer: FrameLayout =
                        requireView().findViewById(R.id.toolbar_container)
                    val alternateToolbar = LayoutInflater.from(context)
                        .inflate(R.layout.search_acton_bar, toolbarContainer, false)
                    toolbarContainer.removeAllViews()
                    toolbarContainer.addView(alternateToolbar)
                    val backArrow: ImageView = requireView().findViewById(R.id.back_arrow)
                    backArrow.setOnClickListener {
                        replaceFragment(MessageFragment(dialog, currentUser))
                    }
                    val icClear: ImageView = requireView().findViewById(R.id.ic_clear)
                    icClear.setOnClickListener {
                        val searchEditText: EditText =
                            requireView().findViewById(R.id.searchEditText)
                        searchEditText.setText("")
                    }
                    val searchEditText: EditText = requireView().findViewById(R.id.searchEditText)
                    searchEditText.addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                            searchMessages(s)
                        }

                        override fun afterTextChanged(s: Editable?) {}
                    })
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun showPopupMenuUnsent(view: View, menuRes: Int, message: Message) {
        val popupMenu = PopupMenu(requireContext(), view)
        popupMenu.menuInflater.inflate(menuRes, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.item_resent -> {
                    lifecycleScope.launch {
                        val localFilePaths = message.localFilePaths
                        val finalMessage: Message = if(localFilePaths != null) {
                            val filePathsFinal = async {
                            when (localFilePaths.size) {
                                1 -> {
                                    val file = viewModel.fManagerGetFile(localFilePaths.first())
                                    if (file != null) {
                                        return@async when {
                                            message.images != null -> {
                                                val (path, f) = viewModel.uploadPhoto(file)
                                                if(f) listOf(path) else null
                                            }
                                            message.file != null -> {
                                                val (path, f) = viewModel.uploadFile(file)
                                                if(f) listOf(path) else null
                                            }
                                            message.voice != null -> {
                                                val (path, f) = viewModel.uploadAudio(file)
                                                if(f) listOf(path) else null
                                            }
                                            else -> null
                                        }
                                    }
                                    else return@async null
                                }
                                in 2..100 -> {
                                    val files = localFilePaths.mapNotNull { filePath ->
                                        viewModel.fManagerGetFile(filePath)
                                    }
                                    val uploadedFiles: MutableList<String> = mutableListOf()
                                    files.forEachIndexed { index, file ->
                                        val tmp = async(Dispatchers.IO) { viewModel.uploadPhoto(file) }
                                        val (path, f) = tmp.await()
                                        if(f) {
                                            uploadedFiles.add(path)
                                        } else {
                                            if(index != 0) {
                                                Toast.makeText(requireContext(), "Файл №$index не загрузился, вторая попытка...", Toast.LENGTH_SHORT).show()
                                                // вторая попытка для файла, если до этого файлы загрузились
                                                val tmp2 = async(Dispatchers.IO) { viewModel.uploadPhoto(file) }
                                                val (path2, f2) = tmp2.await()
                                                if(f2) {
                                                    uploadedFiles.add(path2)
                                                } else {
                                                    Toast.makeText(requireContext(), "Не удалось загрузить файлы", Toast.LENGTH_SHORT).show()
                                                    return@async null
                                                }
                                            } else {
                                                Toast.makeText(requireContext(), "Не удалось загрузить файлы", Toast.LENGTH_SHORT).show()
                                                return@async null
                                            }
                                        }
                                    }
                                    return@async uploadedFiles.ifEmpty { null }
                                }
                                else -> return@async null
                            }
                        }
                            val finalFiles = filePathsFinal.await()
                            if(finalFiles != null) {
                                when {
                                    message.images != null -> {
                                        message.images?.let { viewModel.fManagerDeleteUnsent(it) }
                                        message.copy(images = finalFiles)
                                    }
                                    message.file != null -> {
                                        message.file?.let { viewModel.fManagerDeleteUnsent(listOf(it)) }
                                        message.copy(file = finalFiles.first())
                                    }
                                    message.voice != null -> {
                                        message.voice?.let { viewModel.fManagerDeleteUnsent(listOf(it)) }
                                        message.copy(voice = finalFiles.first())
                                    }
                                    else -> message
                                }
                            } else message
                        } else message
                        val flag = viewModel.sendUnsentMessage(finalMessage)
                        if(flag) {
                            viewModel.deleteUnsentMessage(message.id)
                            adapter.deleteUnsentMessage(message)
                        } else {
                            Toast.makeText(requireContext(), "Не удалось отправить сообщение", Toast.LENGTH_SHORT).show()
                        }
                    }
                    true
                }
                R.id.item_delete -> {
                    lifecycleScope.launch {
                        viewModel.deleteUnsentMessage(message.id)
                        adapter.deleteUnsentMessage(message)
                    }
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun showPopupMenuMessage(view: View, menuRes: Int, message: Message, localMedias: ArrayList<LocalMedia>?, isSender: Boolean) {
        val popupMenu = PopupMenu(requireContext(), view)
        popupMenu.menuInflater.inflate(menuRes, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.item_edit -> {
                    if (message.voice.isNullOrEmpty() && message.file.isNullOrEmpty()) {
                        editFlag = true
                    val editText: EditText = requireView().findViewById(R.id.enter_message)
                    val editButton: ImageView = requireView().findViewById(R.id.edit_button)
                    requireActivity().onBackPressedDispatcher.addCallback(
                        viewLifecycleOwner,
                        object : OnBackPressedCallback(true) {
                            @SuppressLint("NotifyDataSetChanged")
                            override fun handleOnBackPressed() {
                                if (editFlag) {
                                    editText.setText("")
                                    editButton.visibility = View.GONE
                                    binding.recordView.visibility = View.VISIBLE
                                    imageAdapter.clearImages()
                                    editFlag = false
                                } else {
                                    //Removing this callback
                                    remove()
                                    requireActivity().onBackPressedDispatcher.onBackPressed()
                                }
                                viewModel.startRefresh()
                            }
                        })

                    if (!message.text.isNullOrEmpty()) {
                        editText.setText(message.text)
                        editText.setSelection(message.text?.length ?: 0)
                    } else {
                        editText.setText("")
                        editText.setSelection(0)
                        editButton.visibility = View.VISIBLE
                        binding.recordView.visibility = View.INVISIBLE
                    }

                    if (!message.images.isNullOrEmpty()) {
                        imageAdapter.images =
                            ArrayList(localMedias ?: arrayListOf()) // object copy -> remove link
                    }
                    val imm =
                        requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    editText.postDelayed({
                        editText.requestFocus()
                        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
                    }, 100)

                    editButton.setOnClickListener {
                        lifecycleScope.launch {
                            try {
                                viewModel.stopRefresh()
                                val tempItems = imageAdapter.getData()
                                val text = editText.text.toString()
                                if (tempItems.isNotEmpty()) {
                                    val itemsToUpload = if (localMedias == null) tempItems
                                    else tempItems.filter { it !in localMedias } as ArrayList<LocalMedia>
                                    // из-за того, что автор библиотеки дохера умный, приходится использовать кастомный компаратор
                                    val removedItemsIndices: List<Int> =
                                        localMedias?.mapIndexedNotNull { index, localItem ->
                                            if (tempItems.none { tempItem ->
                                                    tempItem.id == localItem.id
                                                            && tempItem.path == localItem.path
                                                            && tempItem.realPath == localItem.realPath
                                                }) index else null
                                        } ?: listOf()

                                    // Upload new media
                                    val uploadList = async {
                                        val list = mutableListOf<String>()
                                        for (uItem in itemsToUpload) {
                                            if (uItem.duration > 0) {
                                                val file = getFileFromContentUri(requireContext(), Uri.parse(uItem.availablePath)) ?: continue
                                                val tmp = async(Dispatchers.IO) {
                                                    viewModel.uploadPhoto(file)
                                                }
                                                val (path, f) = tmp.await()
                                                if(f) list += path
                                            } else {
                                                val tmp = async(Dispatchers.IO) {
                                                    viewModel.uploadPhoto(File(uItem.availablePath))
                                                }
                                                val (path, f) = tmp.await()
                                                if(f) list += path
                                            }
                                        }
                                        return@async list
                                    }
                                    val imagesMessage = message.images?.filterIndexed { index, _ ->
                                        index !in removedItemsIndices
                                    } ?: emptyList()
                                    val finalList = imagesMessage + uploadList.await()
                                    val resp = async {
                                        if (text.isNotEmpty()) {
                                            viewModel.editMessage(message.id, text, finalList, null, null)
                                        } else
                                            viewModel.editMessage(message.id, null, finalList, null, null)
                                    }
                                    val f = resp.await()
                                    editFlag = false
                                    imageAdapter.clearImages()
                                    editText.setText("")
                                    editButton.visibility = View.GONE
                                    binding.recordView.visibility = View.VISIBLE
                                    viewModel.startRefresh()
                                    if (!f) Toast.makeText(requireContext(), "Не удалось редактировать", Toast.LENGTH_SHORT).show()
                                } else {
                                    if (text.isNotEmpty()) {
                                        val resp = async {
                                            viewModel.editMessage(message.id, text, arrayListOf(), null, null)
                                        }
                                        val f = resp.await()
                                        editFlag = false
                                        imageAdapter.clearImages()
                                        editText.setText("")
                                        editButton.visibility = View.GONE
                                        binding.recordView.visibility = View.VISIBLE
                                        viewModel.startRefresh()
                                        if(!f) Toast.makeText(requireContext(), "Не удалось редактировать", Toast.LENGTH_SHORT).show()
                                    } else Toast.makeText(requireContext(), "Сообщение не должно быть пустым", Toast.LENGTH_SHORT).show()
                                }
                            }
                            catch (e: Exception) {
                                Toast.makeText(requireContext(), "Не удалось редактировать", Toast.LENGTH_SHORT).show()
                                editText.setText("")
                                editButton.visibility = View.GONE
                                binding.recordView.visibility = View.VISIBLE
                                imageAdapter.clearImages()
                                editFlag = false
                            }
                        }
                    }
                } else {
                    Toast.makeText(requireContext(), "Файл нельзя редактировать", Toast.LENGTH_SHORT).show()
                    }
                    true
            }
                R.id.item_answer -> {
                    answerFlag = true
                    binding.layoutAnswer.visibility = View.VISIBLE
                    if(isSender) {
                        binding.answerUsername.text = currentUser.username
                        answerMessage = Pair(message.id, currentUser.username)
                    }
                    else {
                        binding.answerUsername.text = dialog.otherUser.username
                        answerMessage = Pair(message.id, dialog.otherUser.username)
                    }
                    if(message.images != null) {
                        binding.answerImageView.visibility = View.VISIBLE
                        lifecycleScope.launch {
                            viewModel.imageSet(message.images!!.first(), binding.answerImageView, requireContext())
                        }
                    }
                    binding.answerMessage.text = when {
                        message.text != null -> message.text
                        message.images != null -> "Фотография"
                        message.file != null -> message.file
                        message.voice != null -> "Голосовое сообщение"
                        else -> "?????????"
                    }
                    binding.icClearAnswer.setOnClickListener {
                        binding.answerUsername.text = ""
                        binding.answerImageView.visibility = View.GONE
                        binding.layoutAnswer.visibility = View.GONE
                        answerFlag = false
                    }
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun disableAnswer() {
        binding.answerUsername.text = ""
        binding.answerImageView.visibility = View.GONE
        binding.layoutAnswer.visibility = View.GONE
        answerFlag = false
    }

    private fun replaceFragment(newFragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, newFragment)
            .commit()
    }

    private fun searchMessages(query: CharSequence?) {
        lifecycleScope.launch {
            if (query.isNullOrEmpty()) {
                viewModel.refresh()
            }
            else if(query.length <= 1) return@launch
            else {
                viewModel.searchMessagesInDialog(query.toString())
            }
        }
    }

    class VerticalSpaceItemDecoration(private val verticalSpaceHeight: Int) :
        RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            super.getItemOffsets(outRect, view, parent, state)
            outRect.bottom = verticalSpaceHeight
        }
    }

    private class MyExternalPreviewEventListener(private val imageAdapter: ImageAdapter) : OnExternalPreviewEventListener {
        override fun onPreviewDelete(position: Int) {
            imageAdapter.remove(position)
            imageAdapter.notifyItemRemoved(position)
        }

        override fun onLongPressDownload(context: Context, media: LocalMedia): Boolean {
            return false
        }
    }

}
