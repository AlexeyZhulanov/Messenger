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
import com.example.messenger.databinding.FragmentMessageBinding
import com.example.messenger.model.Dialog
import com.example.messenger.model.FileNotFoundException
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val typingRunnable = Runnable {
        if (isTyping) {
            viewModel.sendTypingEvent(false)
            isTyping = false
        }
    }
    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)
    private val uiScopeIO = CoroutineScope(Dispatchers.IO + job)
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
                viewModel.combinedFlow.collectLatest { (_, pagingData) ->
                    Log.d("testFlow1", "Submitting paging data")
                    adapter.submitData(pagingData)
                }
            }
            launch {
                viewModel.combinedFlow.collectLatest { (newMessage, _) ->
                    Log.d("testFlow2", "OK")
                    val firstItem = adapter.snapshot().firstOrNull()
                    if(firstItem != null) {
                        val firstMessageTime = firstItem.first.timestamp
                        viewModel.updateLastDate(firstMessageTime)
                    }
                    if (newMessage != null) {
                        Log.d("testFlow3", "Adding new messages")
                        adapter.addNewMessages(newMessage)
                    }
                }
            }
        }
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
                    DialogInfoFragment(dialog, lastSessionString),
                    "DIALOG_INFO_FRAGMENT_TAG"
                )
                .addToBackStack(null)
                .commit()
        }
        val userName: TextView = view.findViewById(R.id.userNameTextView)
        userName.text = dialog.otherUser.username
        userName.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(
                    R.id.fragmentContainer,
                    DialogInfoFragment(dialog, lastSessionString),
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
        viewModel.lastSessionString.observe(viewLifecycleOwner) { sessionString ->
            lastSession.text = sessionString
        }
        lifecycleScope.launch {
            viewModel.readMessagesFlow
                .debounce(5000)
                .distinctUntilChanged()
                .collect { readMessagesIds ->
                adapter.updateMessagesAsRead(readMessagesIds)
            }
        }
        val options: ImageView = view.findViewById(R.id.ic_options)
        options.setOnClickListener {
            showPopupMenu(it, R.menu.popup_menu_dialog)
        }
    }

    @OptIn(FlowPreview::class)
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
        val filePickerManager = FilePickerManager(this)
            adapter = MessageAdapter(object : MessageActionListener {
                override fun onMessageClick(message: Message, itemView: View, isSender: Boolean) {
                    showPopupMenuMessage(itemView, R.menu.popup_menu_message, message, null, isSender)
                }

                override fun onMessageClickImage(message: Message, itemView: View, localMedias: ArrayList<LocalMedia>, isSender: Boolean) {
                    showPopupMenuMessage(itemView, R.menu.popup_menu_message, message, localMedias, isSender)
                }

                override fun onMessageLongClick(itemView: View) {
                    var flag = true
                    uiScope.launch {
                        viewModel.stopRefresh()
                        binding.floatingActionButtonDelete.visibility = View.VISIBLE
                        binding.floatingActionButtonForward.visibility = View.VISIBLE
                        val dialogSettings = async(Dispatchers.IO) { viewModel.getDialogSettings(dialog.id) }
                        adapter.dialogSettings = dialogSettings.await()
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
                       val (messagesToDelete, filesToDelete) = adapter.getDeleteList()
                        if (messagesToDelete.isNotEmpty()) {
                            uiScope.launch {
                                binding.progressBar.visibility = View.VISIBLE
                                val response = async(Dispatchers.IO) { viewModel.deleteMessages(messagesToDelete) }
                                val response2 = withContext(Dispatchers.IO) {
                                    filesToDelete.map {
                                        async { viewModel.deleteFile(it.value, it.key) }
                                    }.awaitAll() // wait all responses
                                }
                                binding.floatingActionButtonDelete.visibility = View.GONE
                                binding.floatingActionButtonForward.visibility = View.GONE
                                if(response.await() && response2.all { it }) {
                                    adapter.clearPositions()
                                    viewModel.startRefresh()
                                    binding.progressBar.visibility = View.GONE
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
            uiScopeIO.launch {
                try {
                    val res = async { filePickerManager.openFilePicker(isCircle = false, isFreeStyleCrop = false, imageAdapter.getData()) }
                    withContext(Dispatchers.Main) {
                        imageAdapter.images = res.await()
                        if(res.await().isNotEmpty()) {
                            binding.recordView.visibility = View.INVISIBLE
                            if(!editFlag) binding.enterButton.visibility = View.VISIBLE
                        }
                    }
                } catch (e: CancellationException) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Выходим...", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Неизвестная ошибка", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        binding.attachButton.setOnLongClickListener {
            ChoosePickFragment(object: ChoosePickListener {
                override fun onGalleryClick() {
                    uiScopeIO.launch {
                        try {
                            val res = async { filePickerManager.openFilePicker(isCircle = false, isFreeStyleCrop = true, imageAdapter.getData()) }
                            withContext(Dispatchers.Main) {
                                imageAdapter.images = res.await()
                                if(res.await().isNotEmpty()) {
                                    binding.recordView.visibility = View.INVISIBLE
                                    binding.enterButton.visibility = View.VISIBLE
                                }
                            }
                        } catch (e: CancellationException) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(requireContext(), "Выходим...", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(requireContext(), "Неизвестная ошибка", Toast.LENGTH_SHORT).show()
                            }
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
        val tryAgainAction: TryAgainAction = { adapter.retry() }
        val headerAdapter = DefaultLoadStateAdapter(tryAgainAction)
        val footerAdapter = DefaultLoadStateAdapter(tryAgainAction)
        val adapterWithLoadStates = adapter.withLoadStateHeaderAndFooter(headerAdapter, footerAdapter)
        binding.recyclerview.layoutManager = layoutManager
        binding.recyclerview.adapter = adapterWithLoadStates
        binding.recyclerview.addItemDecoration(VerticalSpaceItemDecoration(15))
        viewModel.setRecyclerView(binding.recyclerview, adapter)
        binding.selectedPhotosRecyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.selectedPhotosRecyclerView.adapter = imageAdapter
        lifecycleScope.launch {
            adapter.loadStateFlow.debounce(200).collectLatest { _ ->
                headerAdapter.notifyDataSetChanged()
                footerAdapter.notifyDataSetChanged()
            }
        }
        binding.enterButton.setOnClickListener {
            val text = binding.enterMessage.text.toString()
            val items = imageAdapter.getData()
            uiScope.launch {
                if (items.isNotEmpty()) {
                val listik = async {
                val list = mutableListOf<String>()
                    for (item1 in items) {
                        if(item1.duration > 0) {
                            val file = getFileFromContentUri(requireContext(), Uri.parse(item1.availablePath)) ?: continue
                            val tmp = async(Dispatchers.IO) { viewModel.uploadPhoto(file) }
                            list += tmp.await()
                        } else {
                            val tmp = async(Dispatchers.IO) { viewModel.uploadPhoto(File(item1.availablePath)) }
                            list += tmp.await()
                        }
                    }
                    return@async list
                }
                    val list = listik.await()
                    if(text.isNotEmpty()) {
                        if (list.isNotEmpty()) {
                            if(!answerFlag) viewModel.sendMessage(dialog.id, text, list, null, null, null, false, null)
                            else {
                                viewModel.sendMessage(dialog.id, text, list, null, null, answerMessage?.first, false, answerMessage?.second)
                                disableAnswer()
                            }
                        } else {
                            if(!answerFlag) viewModel.sendMessage(dialog.id, text, null, null, null, null, false, null)
                            else {
                                viewModel.sendMessage(dialog.id, text, null, null, null, answerMessage?.first, false, answerMessage?.second)
                                disableAnswer()
                            }
                        }
                    } else if (list.isNotEmpty()) {
                        if(!answerFlag) viewModel.sendMessage(dialog.id, null, list, null, null, null, false, null)
                        else {
                            viewModel.sendMessage(dialog.id, null, list, null, null, answerMessage?.first, false, answerMessage?.second)
                            disableAnswer()
                        }
                    }
                    else withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "Ошибка отправки изображений", Toast.LENGTH_SHORT).show() }
                    imageAdapter.clearImages()
                } else {
                    if(text.isNotEmpty()) {
                        if(!answerFlag) viewModel.sendMessage(dialog.id, text, null, null, null, null, false, null)
                        else {
                            viewModel.sendMessage(dialog.id, text, null, null, null, answerMessage?.first, false, answerMessage?.second)
                            disableAnswer()
                        }
                    }
                }
                binding.recyclerview.adapter?.registerAdapterDataObserver(adapterDataObserver)
                val enterText: EditText = requireView().findViewById(R.id.enter_message)
                enterText.setText("")
                // viewModel.refresh()
            }
        }
        return binding.root
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
        converter.convertPcmToOgg(file.path, fileOgg.path) {success, message ->
            if(success) {
                uiScope.launch {
                    withContext(Dispatchers.IO) {
                        val response = async(Dispatchers.IO) { viewModel.uploadAudio(fileOgg) }
                        if(!answerFlag) viewModel.sendMessage(dialog.id, null, null, response.await(), null, null, false, null)
                        else {
                            viewModel.sendMessage(dialog.id, null, null, response.await(), null, answerMessage?.first, false, answerMessage?.second)
                            withContext(Dispatchers.Main) {
                                disableAnswer()
                            }
                        }
                        withContext(Dispatchers.Main) {
                            binding.recyclerview.adapter?.registerAdapterDataObserver(adapterDataObserver)
                            //viewModel.refresh()
                        }
                    }
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
            val firstMessage = adapter.getItemCustom(firstVisibleItemPosition)?.first
            if(firstMessage?.isRead == true) {
                adapter.getItemCustom(0)?.first?.id ?: -1
            } else {
                firstMessage?.id ?: -1
            }
        } else {
            adapter.getItemCustom(0)?.first?.id ?: -1
        }
        viewModel.saveLastMessage(firstMessageId)
    }

    private fun handleFileUri(uri: Uri) {
        val file = uriToFile(uri, requireContext())
        if(file != null) {
            uiScope.launch {
                val response = async(Dispatchers.IO) { viewModel.uploadFile(file) }
                withContext(Dispatchers.IO) {
                    // костыль чтобы отображалось корректное имя файла - кладу его в voice
                    if(!answerFlag) viewModel.sendMessage(dialog.id, null, null, file.name, response.await(), null, false, null)
                    else {
                        viewModel.sendMessage(dialog.id, null, null, file.name, response.await(), answerMessage?.first, false, answerMessage?.second)
                        withContext(Dispatchers.Main) {
                            disableAnswer()
                        }
                    }
                }
                binding.recyclerview.adapter?.registerAdapterDataObserver(adapterDataObserver)
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
                        uiScope.launch {
                            viewModel.stopRefresh()
                            val tempItems = imageAdapter.getData()
                            val text = editText.text.toString()
                            if (tempItems.isNotEmpty()) {
                                val itemsToUpload = if (localMedias == null) tempItems
                                else tempItems.filter { it !in localMedias } as ArrayList<LocalMedia>
                                // из-за того, что автор библиотеки дохера умный, приходится использовать кастомный компаратор
                                val itemsToDelete = localMedias?.filter { localItem ->
                                    tempItems.none { tempItem ->
                                        tempItem.id == localItem.id
                                                && tempItem.path == localItem.path
                                                && tempItem.realPath == localItem.realPath
                                    }
                                } ?: arrayListOf()
                                // ну и ещё один компаратор...... автор красавчик, сразу понять что у тебя не работает equals нереально
                                val removedItemsIndices: List<Int> =
                                    localMedias?.mapIndexedNotNull { index, localItem ->
                                        if (tempItems.none { tempItem ->
                                                tempItem.id == localItem.id
                                                        && tempItem.path == localItem.path
                                                        && tempItem.realPath == localItem.realPath
                                            }) index else null
                                    } ?: listOf()

                                // Upload new media
                                val uploadList = async(Dispatchers.Main) {
                                    val list = mutableListOf<String>()
                                    for (uItem in itemsToUpload) {
                                        if (uItem.duration > 0) {
                                            val file = getFileFromContentUri(
                                                requireContext(),
                                                Uri.parse(uItem.availablePath)
                                            ) ?: continue
                                            val tmp = async(Dispatchers.IO) {
                                                viewModel.uploadPhoto(file)
                                            }
                                            list += tmp.await()
                                        } else {
                                            val tmp = async(Dispatchers.IO) {
                                                viewModel.uploadPhoto(File(uItem.availablePath))
                                            }
                                            list += tmp.await()
                                        }
                                    }
                                    return@async list
                                }
                                // Delete old media
                                val response = withContext(Dispatchers.IO) {
                                    itemsToDelete.map {
                                        val file = if (it.duration > 0) getFileFromContentUri(
                                            requireContext(),
                                            Uri.parse(it.availablePath)
                                        ) ?: File(it.availablePath)
                                        else File(it.availablePath)
                                        async(Dispatchers.IO) {
                                            try {
                                                viewModel.deleteFile("photos", file.name)
                                            } catch (e: FileNotFoundException) {
                                                return@async true
                                            }
                                        }
                                    }.awaitAll() // wait all responses
                                }
                                val imagesMessage = message.images?.filterIndexed { index, _ ->
                                    index !in removedItemsIndices
                                } ?: emptyList()
                                if (response.all { it }) {
                                    val finalList = imagesMessage + uploadList.await()
                                    val resp = async(Dispatchers.IO) {
                                        if (text.isNotEmpty()) {
                                            viewModel.editMessage(message.id, text, finalList, null, null)
                                        } else
                                            viewModel.editMessage(message.id, null, finalList, null, null)
                                    }
                                    if (resp.await()) {
                                        editFlag = false
                                        imageAdapter.clearImages()
                                        editText.setText("")
                                        editButton.visibility = View.GONE
                                        binding.recordView.visibility = View.VISIBLE
                                        viewModel.startRefresh()
                                    }
                                }
                            } else {
                                if (text.isNotEmpty()) {
                                    val resp = async(Dispatchers.IO) {
                                        viewModel.editMessage(message.id, text, arrayListOf(), null, null)
                                    }
                                    if (resp.await()) {
                                        editFlag = false
                                        imageAdapter.clearImages()
                                        editText.setText("")
                                        editButton.visibility = View.GONE
                                        binding.recordView.visibility = View.VISIBLE
                                        viewModel.startRefresh()
                                    }
                                } else withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        requireContext(),
                                        "Сообщение не должно быть пустым",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
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
                        uiScope.launch {
                            viewModel.imageSet(message.images!!.first(), binding.answerImageView, requireContext())
                        }
                    }
                    binding.answerMessage.text = when {
                        message.text != null -> message.text
                        message.images != null -> "Фотография"
                        message.file != null -> message.voice //имя файла(костыль)
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
        uiScope.launch {
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
