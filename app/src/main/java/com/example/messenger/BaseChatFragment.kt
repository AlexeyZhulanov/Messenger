package com.example.messenger

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.coroutines.cancellation.CancellationException
import com.example.messenger.databinding.FragmentMessageBinding
import com.example.messenger.model.Message
import com.example.messenger.model.User
import com.example.messenger.model.chunkedFlowLast
import com.example.messenger.model.getParcelableCompat
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter

@AndroidEntryPoint
abstract class BaseChatFragment : Fragment(), AudioRecordView.Callback {

    protected lateinit var currentUser: User
    private var isFromNotification: Boolean = false

    protected lateinit var binding: FragmentMessageBinding
    protected lateinit var adapter: MessageAdapter
    private lateinit var imageAdapter: ImageAdapter
    private lateinit var pickFileLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var filePickerManager: FilePickerManager
    private var audioRecord: AudioRecorder? = null
    private var editFlag = false
    private var answerFlag = false
    protected var answerMessage: Pair<Int, String>? = null
    private var typingStoppedTimeout = 3000L // delay 3 seconds
    private var typingHandler: Handler = Handler(Looper.getMainLooper())
    private var isTyping = false
    protected var isStopPagination = false
    private var countNewMsg = 0
    private val typingRunnable = Runnable {
        if (isTyping) {
            sendTypingEvent(false)
            isTyping = false
        }
    }
    private val linkRegex = Regex("""https?://\S+""") // Регулярка для HTTP/HTTPS ссылок
    private val links = mutableSetOf<String>()
    private val namedLinks = mutableMapOf<String, String>()
    private var currentLink: String? = null
    private var currentLinkNumber = 0

    abstract val viewModel: BaseChatViewModel

    abstract fun sendTypingEvent(isSend: Boolean)
    abstract fun replaceToInfoFragment()
    abstract fun getAvatarString(): String
    abstract fun getUpperName(): String
    abstract fun getUserName(): String
    abstract fun composeAnswer(message: Message)
    abstract fun getMembers(): List<User>
    abstract fun setupAdapterDialog()
    abstract fun isGroup(): Boolean
    abstract fun canDelete(): Boolean
    abstract fun getUnreadCount(): Int

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

    private val adapterArrowObserver = object : RecyclerView.AdapterDataObserver() {
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            val firstVisiblePosition = (binding.recyclerview.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
            if(firstVisiblePosition > 2) {
                countNewMsg++
                showArrow()
            } else binding.recyclerview.scrollToPosition(0)
            binding.recyclerview.adapter?.unregisterAdapterDataObserver(this)
        }
    }

    private val adapterInitialListObserver = object: RecyclerView.AdapterDataObserver() {
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            countNewMsg = getUnreadCount() - 3
            binding.recyclerview.scrollToPosition(getUnreadCount() - 2)
            showArrow()
            binding.recyclerview.adapter?.unregisterAdapterDataObserver(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentUser = arguments?.getParcelableCompat<User>(ARG_USER) ?: User(0, "", "")
        isFromNotification = requireArguments().getBoolean(ARG_FROM_NOTIFICATION)

        pickFileLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri>? ->
            if (uris != null) {
                if (uris.size > 5) {
                    Toast.makeText(requireContext(), "Можно выбрать не более 5 файлов", Toast.LENGTH_SHORT).show()
                } else {
                    uris.forEach { handleFileUri(it) }
                }
            }
        }
    }

    @OptIn(FlowPreview::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            launch {
                viewModel.newMessageFlow
                    .buffer()
                    .chunkedFlowLast(200) // custom func
                    .collect { newMessages ->
                        if(newMessages.isNotEmpty()) {
                            registerArrowObserver()
                            val clearMessages = newMessages.filterNotNull()
                            adapter.addNewMessages(clearMessages)
                            val unreadMessages = clearMessages.filter {
                                if(it.first.isPersonalUnread == null) {
                                    currentUser.id != it.first.idSender && !it.first.isRead
                                } else {
                                    currentUser.id != it.first.idSender && it.first.isPersonalUnread == true
                                }
                            }
                            if (unreadMessages.isNotEmpty()) {
                                viewModel.markMessagesAsRead(unreadMessages.map { it.first })
                            }
                        }
                    }
            }
            launch {
                viewModel.unsentMessageFlow.collect { uMessage ->
                    if(uMessage != null) {
                        registerScrollObserver()
                        Log.d("testUnsentFlow", "OK")
                        adapter.addUnsentMessage(Triple(uMessage, "", ""))
                    }
                }
            }
            launch {
                viewModel.editMessageFlow.collect { message ->
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
            launch {
                viewModel.readMessagesFlow.collect { readMessagesIds ->
                    adapter.updateMessagesAsRead(readMessagesIds)
                }
            }
            launch {
                viewModel.deleteState.collectLatest {
                    if(it == 1) {
                        Toast.makeText(requireContext(), "Все сообщения были удалены", Toast.LENGTH_SHORT).show()
                    }
                    if(it == 2) {
                        Toast.makeText(requireContext(), "Диалог был удален", Toast.LENGTH_SHORT).show()
                        backPressed()
                    }
                }
            }
        }

        binding.floatingActionButtonArrowDown.setOnClickListener {
            binding.recyclerview.smoothScrollToPosition(0)
            binding.floatingActionButtonArrowDown.visibility = View.GONE
        }

        registerArrowScrollListener()
        val typedValue = TypedValue()
        requireActivity().theme.resolveAttribute(R.attr.colorBar, typedValue, true)
        val colorBar = typedValue.data
        requireActivity().window.statusBarColor = colorBar
        requireActivity().window.navigationBarColor = ContextCompat.getColor(requireContext(), R.color.navigation_bar_color)
        val toolbarContainer: FrameLayout = view.findViewById(R.id.toolbar_container)
        val defaultToolbar = LayoutInflater.from(context)
            .inflate(R.layout.custom_action_bar, toolbarContainer, false)
        toolbarContainer.addView(defaultToolbar)
        val backArrow: ImageView = view.findViewById(R.id.back_arrow)
        backArrow.setOnClickListener {
            backPressed()
        }
        val profilePhoto: ImageView = view.findViewById(R.id.photoImageView)
        profilePhoto.setOnClickListener {
            binding.enterMessage.isEnabled = false // необходимо для предотвращения багов из-за клавиатуры
            replaceToInfoFragment()
        }
        val icVolumeOff: ImageView = view.findViewById(R.id.ic_volume_off)
        lifecycleScope.launch {
            icVolumeOff.visibility = if(viewModel.isNotificationsEnabled()) View.GONE else View.VISIBLE
            val avatar = getAvatarString()
            viewModel.avatarSet(avatar, profilePhoto, requireContext())
        }
        val userName: TextView = view.findViewById(R.id.userNameTextView)
        userName.text = getUpperName()
        userName.setOnClickListener {
            binding.enterMessage.isEnabled = false
            replaceToInfoFragment()
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
                .collect { (isTyping, username) ->
                    if (isTyping) {
                        lastSession.visibility = View.INVISIBLE
                        typingTextView.text = if(username != null) "$username печатает" else "печатает"
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
        val options: ImageView = view.findViewById(R.id.ic_options)
        options.setOnClickListener {
            showPopupMenu(it, R.menu.popup_menu_dialog)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentMessageBinding.inflate(inflater, container, false)
        val wallpaper = viewModel.getWallpaper(isDarkTheme(requireContext()))
        if (wallpaper != "") {
            val resId = WALLPAPER_MAP[wallpaper] ?: -1
            if(resId != -1)
                binding.messageLayout.background = ContextCompat.getDrawable(requireContext(), resId)
        }
        filePickerManager = FilePickerManager(this)
        setupAdapterDialog()
        if(isFromNotification) {
            requireActivity().onBackPressedDispatcher.addCallback(
                viewLifecycleOwner,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        backPressed()
                        remove()
                    }
                })
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
            private var lastText = ""

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString()?.trim()

                if (text.isNullOrEmpty()) {
                    binding.recordView.visibility = View.VISIBLE
                    if (!editFlag) {
                        binding.enterButton.visibility = View.INVISIBLE
                    } else {
                        binding.editButton.visibility = View.GONE
                    }

                    // Отправляем событие "typing_stopped"
                    sendTypingEvent(false)
                    isTyping = false
                    typingHandler.removeCallbacks(typingRunnable)
                    links.clear()
                    namedLinks.clear()
                    currentLink = null
                    binding.linkLayout.visibility = View.GONE
                } else {
                    binding.recordView.visibility = View.INVISIBLE
                    if (!editFlag) {
                        binding.enterButton.visibility = View.VISIBLE
                    } else {
                        binding.editButton.visibility = View.VISIBLE
                    }

                    if (!isTyping) {
                        // Отправляем событие "typing_started"
                        sendTypingEvent(true)
                        isTyping = true
                    }

                    // Перезапускаем таймер отсчета до отправки "typing_stopped"
                    typingHandler.removeCallbacks(typingRunnable)
                    typingHandler.postDelayed(typingRunnable, typingStoppedTimeout)

                    val currentText = s.toString()
                    if (currentText != lastText) {
                        val newLinks = linkRegex.findAll(s).map { it.value }.toSet()

                        // Удаляем ссылки, которых больше нет в тексте
                        val removedLinks = links.filter { it !in newLinks }.toSet()
                        links.removeAll(removedLinks)

                        // Если текущая ссылка удалена — переключаем на предыдущую
                        if (currentLink in removedLinks) {
                            currentLink = links.lastOrNull()
                            currentLinkNumber = links.size
                        }

                        newLinks.forEach { link ->
                            if (!links.contains(link)) {
                                if(links.isEmpty()) binding.linkLayout.visibility = View.VISIBLE
                                links.add(link)
                                if (currentLink == null) {
                                    currentLink = link
                                    currentLinkNumber = 1
                                }
                                Log.d("testLinkAdded", "Новая ссылка: $link")
                            }
                        }
                        updateLinkLayout()
                        lastText = currentText
                    }
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.upImageView.setOnClickListener {
            if(links.size > 1 && currentLinkNumber != 1) {
                val currentIndex = links.indexOf(currentLink)
                currentLink?.let {
                    namedLinks[it] = binding.namedLinkEditText.text.toString()
                }
                currentLinkNumber = (currentIndex).takeIf { it >= 0 } ?: links.size
                currentLink = links.elementAt(currentLinkNumber - 1)
                updateLinkLayout()
                Log.d("testClick", "Стрелка вверх нажата!")
            }
        }

        binding.downImageView.setOnClickListener {
            if(links.size > 1 && currentLinkNumber != links.size) {
                val currentIndex = links.indexOf(currentLink)
                currentLink?.let {
                    namedLinks[it] = binding.namedLinkEditText.text.toString()
                }
                currentLinkNumber = (currentIndex + 2).takeIf { it < links.size } ?: links.size
                currentLink = links.elementAt(currentLinkNumber - 1)
                updateLinkLayout()
                Log.d("testClick", "Стрелка вниз нажата!")
            }
        }

        binding.recordView.apply {
            activity = requireActivity()
            callback = this@BaseChatFragment
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
            val fragmentChoosePick = ChoosePickFragment()

            fragmentChoosePick.setListener(object: ChoosePickListener {
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
                override fun onCodeClick() {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, CodeFragment.newInstance(viewModel), "CODE_FRAGMENT_TAG")
                        .addToBackStack(null)
                        .commit()
                }
            })
            fragmentChoosePick.show(childFragmentManager, "ChoosePickTag")
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
            initialPrefetchItemCount = 10 // не особо заметил разницы с и без
        }
        binding.recyclerview.layoutManager = layoutManager
        binding.recyclerview.addItemDecoration(VerticalSpaceItemDecoration(15))
        binding.recyclerview.setItemViewCacheSize(30) // works good
        viewModel.bindRecyclerView(binding.recyclerview)
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
            binding.enterButton.isEnabled = false
            binding.progressBarEnter.visibility = View.VISIBLE
            val (text, isLink) = prepareTextForSend(binding.enterMessage.text.toString())
            val items = imageAdapter.getData()

            lifecycleScope.launch {
                if (items.isNotEmpty()) {
                    val listik = async {
                        val list = mutableListOf<String>()
                        var flag = true
                        val localFilePaths = mutableListOf<String>()
                        for (item1 in items) {
                            if (item1.duration > 0) {
                                val file = viewModel.getFileFromContentUri(requireContext(), Uri.parse(item1.availablePath)) ?: continue
                                val tmp = async { viewModel.uploadPhoto(file, requireContext(), true) }
                                val (path, f) = tmp.await()
                                list += path
                                flag = f
                                if(!f) break
                            } else {
                                val tmp = async { viewModel.uploadPhoto(File(item1.availablePath), requireContext(), false) }
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
                                        viewModel.getFileFromContentUri(requireContext(), Uri.parse(item1.availablePath)) ?: continue
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
                            if(!answerFlag) viewModel.sendMessage(text, list, null, null, null, null, null, false, isLink, null, localFilePaths)
                            else {
                                viewModel.sendMessage(text, list, null, null, null, null, answerMessage?.first, false, isLink, answerMessage?.second, localFilePaths)
                                disableAnswer()
                            }
                        } else {
                            if(!answerFlag) viewModel.sendMessage(text, null, null, null, null, null, null, false, isLink, null, null)
                            else {
                                viewModel.sendMessage(text, null, null, null, null, null, answerMessage?.first, false, isLink, answerMessage?.second, null)
                                disableAnswer()
                            }
                        }
                    } else if (list.isNotEmpty()) {
                        if(!answerFlag) viewModel.sendMessage(null, list, null, null, null, null, null, false, null, null, localFilePaths)
                        else {
                            viewModel.sendMessage(null, list, null, null, null, null, answerMessage?.first, false, null, answerMessage?.second, localFilePaths)
                            disableAnswer()
                        }
                    }
                    else Toast.makeText(requireContext(), "Ошибка отправки изображений", Toast.LENGTH_SHORT).show()
                    imageAdapter.clearImages()
                } else {
                    if(text.isNotEmpty()) {
                        if(!answerFlag) viewModel.sendMessage(text, null, null, null, null, null, null, false, isLink, null, null)
                        else {
                            viewModel.sendMessage(text, null, null, null, null, null, answerMessage?.first, false, isLink, answerMessage?.second, null)
                            disableAnswer()
                        }
                    }
                }
                binding.enterMessage.setText("")
                binding.progressBarEnter.visibility = View.GONE
                binding.enterButton.isEnabled = true
            }
        }
        return binding.root
    }

    private fun registerScrollObserver() {
        try {
            binding.recyclerview.adapter?.registerAdapterDataObserver(adapterDataObserver)
        } catch (e: Exception) {
            binding.recyclerview.adapter?.unregisterAdapterDataObserver(adapterDataObserver)
            binding.recyclerview.adapter?.registerAdapterDataObserver(adapterDataObserver)
        }
    }

    private fun registerArrowObserver() {
        try {
            binding.recyclerview.adapter?.registerAdapterDataObserver(adapterArrowObserver)
        } catch (e: Exception) {
            binding.recyclerview.adapter?.unregisterAdapterDataObserver(adapterArrowObserver)
            binding.recyclerview.adapter?.registerAdapterDataObserver(adapterArrowObserver)
        }
    }

    protected fun registerInitialListObserver() {
        try {
            binding.recyclerview.adapter?.registerAdapterDataObserver(adapterInitialListObserver)
        } catch (e: Exception) {
            binding.recyclerview.adapter?.unregisterAdapterDataObserver(adapterInitialListObserver)
            binding.recyclerview.adapter?.registerAdapterDataObserver(adapterInitialListObserver)
        }
    }

    private fun registerArrowScrollListener() {
        binding.recyclerview.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (firstVisibleItemPosition > 2) {
                    if(countNewMsg >= firstVisibleItemPosition) countNewMsg = firstVisibleItemPosition - 1
                    showArrow()
                } else {
                    hideArrow()
                }
            }
        })
    }

    private fun showArrow() {
        if(binding.floatingActionButtonArrowDown.visibility == View.GONE) {
            binding.floatingActionButtonArrowDown.visibility = View.VISIBLE
        }
        if(countNewMsg > 0) {
            if(binding.countNewMsgTextView.visibility == View.GONE) {
                binding.countNewMsgTextView.visibility = View.VISIBLE
            }
            val strCount = countNewMsg.toString()
            binding.countNewMsgTextView.text = strCount
        } else if(binding.countNewMsgTextView.visibility == View.VISIBLE) {
            binding.countNewMsgTextView.visibility = View.GONE
        }
    }

    private fun hideArrow() {
        if(binding.floatingActionButtonArrowDown.visibility == View.VISIBLE) {
            binding.floatingActionButtonArrowDown.visibility = View.GONE
        }
        binding.countNewMsgTextView.visibility = View.GONE
        countNewMsg = 0
    }

    private fun updateLinkLayout() {
        with(binding) {
            if (links.isEmpty()) {
                linkLayout.visibility = View.GONE
                return
            }
            linkLayout.visibility = View.VISIBLE
            val linkNumbStr = currentLinkNumber.toString()
            currentLinkTextView.text = linkNumbStr
            val linkSizeStr = links.size.toString()
            linkCountTextView.text = linkSizeStr
            linkTextView.text = currentLink ?: ""
            val str = namedLinks[currentLink]
            if(!str.isNullOrEmpty()) namedLinkEditText.setText(str) else namedLinkEditText.text.clear()
        }
    }

    private fun prepareTextForSend(rawText: String): Pair<String, Boolean> {
        val messageText = rawText.trim()

        if(links.isEmpty()) return messageText to false

        var processedText = messageText

        linkRegex.findAll(messageText).forEach { match ->
            val url = match.value
            val name = namedLinks[url]
            if(!name.isNullOrEmpty()) {
                processedText = processedText.replace(url, "[$name]($url)")
            } else {
                val unsavedName = binding.namedLinkEditText.text.toString()
                if(unsavedName.isNotEmpty()) {
                    processedText = processedText.replace(url, "[$unsavedName]($url)")
                    binding.namedLinkEditText.text.clear()
                }
            }
        }
        return processedText to true
    }

    private fun backPressed() {
        if(isFromNotification) {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, MessengerFragment())
                .commit()
        } else requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    protected fun setupAdapter(members: List<User>) {
        adapter = MessageAdapter(object : MessageActionListener {
            override fun onUnsentMessageClick(message: Message, itemView: View) {
                showPopupMenuUnsent(itemView, R.menu.popup_menu_unsent, message)
            }

            override fun onMessageClick(message: Message, itemView: View, isSender: Boolean) {
                if(isSender) showPopupMenuMessage(itemView, R.menu.popup_menu_message, message, null, true)
                else showPopupMenuMessage(itemView, R.menu.popup_menu_message_receiver, message, null, false)
            }

            override fun onMessageClickImage(message: Message, itemView: View, localMedias: ArrayList<LocalMedia>, isSender: Boolean) {
                if(isSender) showPopupMenuMessage(itemView, R.menu.popup_menu_message, message, localMedias, true)
                else showPopupMenuMessage(itemView, R.menu.popup_menu_message_receiver, message, localMedias, false)
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
                                    backPressed()
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
                            val uName = getUserName()
                            if(members.isEmpty()) {
                                booleans.forEach {
                                    if(it) strings.add(currentUser.username)
                                    else strings.add(uName)
                                }
                            } else {
                                messages.forEachIndexed { index, message ->
                                    if(booleans[index]) strings.add(currentUser.username)
                                    else {
                                        val username = members.find { it.id == message.idSender }?.username
                                        strings.add(username ?: "")
                                    }
                                }
                            }
                            val bundle = Bundle().apply {
                                putParcelableArrayList("forwardedMessages", ArrayList(messages))
                                putStringArrayList("forwardedUsernames", ArrayList(strings))
                            }
                            parentFragmentManager.setFragmentResult("forwardMessagesRequestKey", bundle)
                            backPressed()
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
                    .isAutoVideoPlay(true)
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
            override fun onCodeOpenClick(message: Message) {
                val code = message.code
                val lang = message.codeLanguage
                if(code != null && lang != null) {
                    val dialog = CodePreviewDialogFragment.newInstance(code, lang)
                    dialog.show(parentFragmentManager, "CodePreviewDialogFragment")
                }
            }
        }, currentUser.id, requireContext(), viewModel, isGroup(), canDelete())
        adapter.setHasStableIds(true)
        binding.recyclerview.adapter = adapter
    }

    override val defaultViewModelCreationExtras: CreationExtras
        get() = super.defaultViewModelCreationExtras

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
                        val (path, f) = viewModel.uploadAudio(fileOgg, requireContext())
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
                    if(!answerFlag) viewModel.sendMessage(null, null, first, null, null, null, null, false, null, null, second)
                    else {
                        viewModel.sendMessage(null, null, first, null, null, null, answerMessage?.first, false, null, answerMessage?.second, second)
                        disableAnswer()
                    }
                    registerScrollObserver()
                }
            } else {
                Log.d("testConvert", "Not OK")
            }
        }
    }

    override fun onRecordStart() {
        clearFile(tmpFile)

        audioRecord = AudioRecorder(ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_WRITE))
        audioRecord?.start(this, null)
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
    }

    private fun handleFileUri(uri: Uri) {
        val file = viewModel.uriToFile(uri, requireContext())
        if(file != null) {
            lifecycleScope.launch {
                val response = async {
                    val (path, f) = viewModel.uploadFile(file, requireContext())
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
                if(!answerFlag) viewModel.sendMessage(null, null, null, first, null, null, null, false, null, null, second)
                else {
                    viewModel.sendMessage(null, null, file.name, first, null, null, answerMessage?.first, false, null, answerMessage?.second, second)
                    disableAnswer()
                }
                registerScrollObserver()
            }
        } else Toast.makeText(requireContext(), "Что-то не так с файлом", Toast.LENGTH_SHORT).show()
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
                    toolbarContainer.addView(alternateToolbar)
                    val backArrow: ImageView = requireView().findViewById(R.id.back_arrow)
                    backArrow.setOnClickListener {
                        getBackFromSearch(alternateToolbar)
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
                                                    val (path, f) = viewModel.uploadPhoto(file, requireContext(), viewModel.isVideoFile(file))
                                                    if(f) listOf(path) else null
                                                }
                                                message.file != null -> {
                                                    val (path, f) = viewModel.uploadFile(file, requireContext())
                                                    if(f) listOf(path) else null
                                                }
                                                message.voice != null -> {
                                                    val (path, f) = viewModel.uploadAudio(file, requireContext())
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
                                            val tmp = async { viewModel.uploadPhoto(file, requireContext(), viewModel.isVideoFile(file)) }
                                            val (path, f) = tmp.await()
                                            if(f) {
                                                uploadedFiles.add(path)
                                            } else {
                                                if(index != 0) {
                                                    Toast.makeText(requireContext(), "Файл №$index не загрузился, вторая попытка...", Toast.LENGTH_SHORT).show()
                                                    // вторая попытка для файла, если до этого файлы загрузились
                                                    val tmp2 = async { viewModel.uploadPhoto(file, requireContext(), viewModel.isVideoFile(file)) }
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
                        if(!message.codeLanguage.isNullOrEmpty()) {
                            parentFragmentManager.beginTransaction()
                                .replace(R.id.fragmentContainer, CodeFragment.newInstance(viewModel, message), "CODE_FRAGMENT_TAG2")
                                .addToBackStack(null)
                                .commit()
                        }
                        editFlag = true
                        requireActivity().onBackPressedDispatcher.addCallback(
                            viewLifecycleOwner,
                            object : OnBackPressedCallback(true) {
                                @SuppressLint("NotifyDataSetChanged")
                                override fun handleOnBackPressed() {
                                    if (editFlag) {
                                        binding.enterMessage.setText("")
                                        binding.editButton.visibility = View.GONE
                                        binding.recordView.visibility = View.VISIBLE
                                        imageAdapter.clearImages()
                                        editFlag = false
                                    } else {
                                        //Removing this callback
                                        remove()
                                        backPressed()
                                    }
                                    viewModel.startRefresh()
                                }
                            })

                        if (!message.text.isNullOrEmpty()) {
                            binding.enterMessage.setText(message.text)
                            binding.enterMessage.setSelection(message.text?.length ?: 0)
                        } else {
                            binding.enterMessage.setText("")
                            binding.enterMessage.setSelection(0)
                            binding.editButton.visibility = View.VISIBLE
                            binding.recordView.visibility = View.INVISIBLE
                        }

                        if (!message.images.isNullOrEmpty()) {
                            imageAdapter.images =
                                ArrayList(localMedias ?: arrayListOf()) // object copy -> remove link
                        }
                        val imm =
                            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        binding.enterMessage.postDelayed({
                            binding.enterMessage.requestFocus()
                            imm.showSoftInput(binding.enterMessage, InputMethodManager.SHOW_IMPLICIT)
                        }, 100)

                        binding.editButton.setOnClickListener {
                            lifecycleScope.launch {
                                try {
                                    viewModel.stopRefresh()
                                    val tempItems = imageAdapter.getData()
                                    val (text, isLink) = prepareTextForSend(binding.enterMessage.text.toString())
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
                                                    val file = viewModel.getFileFromContentUri(requireContext(), Uri.parse(uItem.availablePath)) ?: continue
                                                    val tmp = async {
                                                        viewModel.uploadPhoto(file, requireContext(), true)
                                                    }
                                                    val (path, f) = tmp.await()
                                                    if(f) list += path
                                                } else {
                                                    val tmp = async {
                                                        viewModel.uploadPhoto(File(uItem.availablePath), requireContext(), false)
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
                                                viewModel.editMessage(message.id, text, finalList, null, null, null, null, isLink)
                                            } else
                                                viewModel.editMessage(message.id, null, finalList, null, null, null, null, null)
                                        }
                                        val f = resp.await()
                                        editFlag = false
                                        imageAdapter.clearImages()
                                        binding.enterMessage.setText("")
                                        binding.editButton.visibility = View.GONE
                                        binding.recordView.visibility = View.VISIBLE
                                        viewModel.startRefresh()
                                        if (!f) Toast.makeText(requireContext(), "Не удалось редактировать", Toast.LENGTH_SHORT).show()
                                    } else {
                                        if (text.isNotEmpty()) {
                                            val resp = async {
                                                viewModel.editMessage(message.id, text, null, null, null, null, null, isLink)
                                            }
                                            val f = resp.await()
                                            editFlag = false
                                            imageAdapter.clearImages()
                                            binding.enterMessage.setText("")
                                            binding.editButton.visibility = View.GONE
                                            binding.recordView.visibility = View.VISIBLE
                                            viewModel.startRefresh()
                                            if(!f) Toast.makeText(requireContext(), "Не удалось редактировать", Toast.LENGTH_SHORT).show()
                                        } else Toast.makeText(requireContext(), "Сообщение не должно быть пустым", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                catch (e: Exception) {
                                    Toast.makeText(requireContext(), "Не удалось редактировать", Toast.LENGTH_SHORT).show()
                                    binding.enterMessage.setText("")
                                    binding.editButton.visibility = View.GONE
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
                        composeAnswer(message)
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
                R.id.item_copy -> {
                    message.text?.let {
                        val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("label", it)
                        clipboard.setPrimaryClip(clip)
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

    private fun getBackFromSearch(view: View) {
        val toolbarContainer: FrameLayout = requireView().findViewById(R.id.toolbar_container)
        toolbarContainer.removeView(view)
        viewModel.refresh()
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

    private class MyExternalPreviewEventListener(private val imageAdapter: ImageAdapter) :
        OnExternalPreviewEventListener {
        override fun onPreviewDelete(position: Int) {
            imageAdapter.remove(position)
            imageAdapter.notifyItemRemoved(position)
        }

        override fun onLongPressDownload(context: Context, media: LocalMedia): Boolean {
            return false
        }
    }

    private fun isDarkTheme(context: Context): Boolean {
        return when (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
    }

    companion object {
        const val ARG_USER = "arg_user"
        const val ARG_FROM_NOTIFICATION = "arg_from_notification"

        private val WALLPAPER_MAP = mapOf(
            "wallpaper1" to R.drawable.wallpaper1,
            "wallpaper2" to R.drawable.wallpaper2,
            "wallpaper3" to R.drawable.wallpaper3,
            "wallpaper4" to R.drawable.wallpaper4,
            "wallpaper5" to R.drawable.wallpaper5,
            "wallpaper6" to R.drawable.wallpaper6,
            "wallpaper7" to R.drawable.wallpaper7,
            "wallpaper8" to R.drawable.wallpaper8,
            "wallpaper9" to R.drawable.wallpaper9,
            "wallpaper10" to R.drawable.wallpaper10,
            "wallpaper11" to R.drawable.wallpaper11,
            "wallpaper12" to R.drawable.wallpaper12
        )
    }
}