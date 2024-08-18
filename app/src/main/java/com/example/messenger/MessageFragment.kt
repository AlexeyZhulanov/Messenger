package com.example.messenger

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
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
import androidx.core.net.toFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.messenger.databinding.FragmentMessageBinding
import com.example.messenger.model.Dialog
import com.example.messenger.model.FileNotFoundException
import com.example.messenger.model.Message
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

class MessageFragment(
    private val dialog: Dialog
) : Fragment(), AudioRecordView.Callback {
    private lateinit var binding: FragmentMessageBinding
    private lateinit var adapter: MessageAdapter
    private lateinit var imageAdapter: ImageAdapter
    private lateinit var preferences: SharedPreferences
    private lateinit var pickFileLauncher: ActivityResultLauncher<Array<String>>
    private var audioRecord: AudioRecorder? = null
    private var lastSessionString: String = ""
    private var countMsg = dialog.countMsg
    private var editFlag = false
    private var updateJob: Job? = null
    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)
    private val uiScopeIO = CoroutineScope(Dispatchers.IO + job)
    private val messengerService: MessengerService
        get() = Singletons.messengerRepository as MessengerService
    private val retrofitService: RetrofitService
        get() = Singletons.retrofitRepository as RetrofitService

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


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pickFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) {uri: Uri? ->
            uri?.let {
                handleFileUri(uri) // обработка выбранного файла
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
        lifecycleScope.launch {
            lastSessionString =
                formatUserSessionDate(retrofitService.getLastSession(dialog.otherUser.id))
            lastSession.text = lastSessionString
        }
        val options: ImageView = view.findViewById(R.id.ic_options)
        options.setOnClickListener {
            showPopupMenu(it, R.menu.popup_menu_dialog)
        }
    }

    @SuppressLint("InflateParams")
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
                override fun onMessageClick(message: Message, itemView: View) {
                    showPopupMenuMessage(itemView, R.menu.popup_menu_message, message, null)
                }

                override fun onMessageClickImage(message: Message, itemView: View, localMedias: ArrayList<LocalMedia>) {
                    showPopupMenuMessage(itemView, R.menu.popup_menu_message, message, localMedias)
                }

                override fun onMessageLongClick(itemView: View) {
                    uiScope.launch {
                        stopMessagePolling()
                        binding.floatingActionButtonDelete.visibility = View.VISIBLE
                        val dialogSettings = async(Dispatchers.IO) {retrofitService.getDialogSettings(dialog.id)}
                        adapter.dialogSettings = dialogSettings.await()
                    requireActivity().onBackPressedDispatcher.addCallback(
                        viewLifecycleOwner,
                        object : OnBackPressedCallback(true) {
                            @SuppressLint("NotifyDataSetChanged")
                            override fun handleOnBackPressed() {
                                if (!adapter.canLongClick) {
                                    adapter.clearPositions()
                                    binding.floatingActionButtonDelete.visibility = View.GONE
                                } else {
                                    //Removing this callback
                                    remove()
                                    requireActivity().onBackPressedDispatcher.onBackPressed()
                                }
                                startMessagePolling()
                            }
                        })
                    binding.floatingActionButtonDelete.setOnClickListener {
                       val (messagesToDelete, filesToDelete) = adapter.getDeleteList()
                        if (messagesToDelete.isNotEmpty()) {
                            uiScope.launch {
                                binding.progressBar.visibility = View.VISIBLE
                                val response = async(Dispatchers.IO) { retrofitService.deleteMessages(messagesToDelete) }
                                val response2 = withContext(Dispatchers.IO) {
                                    filesToDelete.map {
                                        async { retrofitService.deleteFile(it.value, it.key) }
                                    }.awaitAll() // wait all responses
                                }
                                binding.floatingActionButtonDelete.visibility = View.GONE
                                countMsg -= messagesToDelete.size
                                if(response.await() && response2.all { it }) {
                                    adapter.clearPositions()
                                    startMessagePolling()
                                    binding.progressBar.visibility = View.GONE
                                }
                            }
                        }
                    }
                }
                }
                override fun onImagesClick(images: ArrayList<LocalMedia>, position: Int) {
                    Log.d("testClickImages", "images: $images")
                    PictureSelector.create(requireContext())
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
            }, dialog.otherUser.id, requireContext())
        imageAdapter = ImageAdapter(requireContext(), object: ImageActionListener {
            override fun onImageClicked(image: LocalMedia, position: Int) {
                Log.d("testClick", "Image clicked: $image")
                PictureSelector.create(requireContext())
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
                    if(!editFlag) {
                        binding.enterButton.visibility = View.INVISIBLE
                    } else binding.editButton.visibility = View.GONE
                } else {
                    binding.recordView.visibility = View.INVISIBLE
                    if(!editFlag) {
                        binding.enterButton.visibility = View.VISIBLE
                    } else binding.editButton.visibility = View.VISIBLE
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
                binding.emojiPicker.visibility = View.VISIBLE
                binding.emojiPicker.setOnEmojiPickedListener { emojicon ->
                    val emoji = emojicon.emoji
                }
            }
        }

        val layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerview.layoutManager = layoutManager
        binding.recyclerview.adapter = adapter
        binding.recyclerview.addItemDecoration(VerticalSpaceItemDecoration(15))
        binding.selectedPhotosRecyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.selectedPhotosRecyclerView.adapter = imageAdapter
        binding.enterButton.setOnClickListener {
            val text = binding.enterMessage.text.toString()
            val items = imageAdapter.getData()
            uiScope.launch {
                if (items.isNotEmpty()) {
                    imageAdapter.clearImages()
                val listik = async {
                val list = mutableListOf<String>()
                    for (item1 in items) {
                        if(item1.duration > 0) {
                            val file = getFileFromContentUri(requireContext(), Uri.parse(item1.availablePath)) ?: continue
                            val tmp = async(Dispatchers.IO) { retrofitService.uploadPhoto(file) }
                            list += tmp.await()
                        } else {
                            val tmp = async(Dispatchers.IO) { retrofitService.uploadPhoto(File(item1.availablePath)) }
                            list += tmp.await()
                        }
                    }
                    return@async list
                }
                    val list = listik.await()
                    if(text.isNotEmpty()) {
                        if (list.isNotEmpty()) {
                            retrofitService.sendMessage(dialog.id, text, list, null, null)
                        } else {
                            retrofitService.sendMessage(dialog.id, text, null, null, null)
                        }
                    } else if (list.isNotEmpty()) retrofitService.sendMessage(dialog.id, null, list, null, null)
                    else withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "Ошибка отправки изображений", Toast.LENGTH_SHORT).show() }
            } else {
                    if(text.isNotEmpty()) {
                        retrofitService.sendMessage(dialog.id, text, null, null, null)
                    }
                }
                countMsg += 1
                val enterText: EditText = requireView().findViewById(R.id.enter_message)
                enterText.setText("")
                adapter.messages = retrofitService.getMessages(dialog.id, 0, countMsg).associateWith { "" }
                binding.recyclerview.post {
                    binding.recyclerview.scrollToPosition(adapter.itemCount - 1)
                }
            }
        }
        retrofitService.initCompleted.observe(viewLifecycleOwner) { initCompleted ->
            if (initCompleted) {
                startMessagePolling()
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
                        val response = async(Dispatchers.IO) { retrofitService.uploadAudio(fileOgg) }
                        retrofitService.sendMessage(dialog.id, null, null, response.await(), null)
                        withContext(Dispatchers.Main) {
                            countMsg += 1
                            adapter.messages =
                                retrofitService.getMessages(dialog.id, 0, countMsg).associateWith { "" }
                            binding.recyclerview.post {
                                binding.recyclerview.scrollToPosition(adapter.itemCount - 1)
                            }
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

    private fun startMessagePolling() {
        updateJob = lifecycleScope.launch {
            while (isActive) {
                val temp = async(Dispatchers.IO) { retrofitService.getMessages(dialog.id, 0, countMsg).associateWith { "" } } //todo pagination
                adapter.messages = temp.await()
                delay(30000)
            }
        }
    }

    private fun stopMessagePolling() {
        updateJob?.cancel()
        updateJob = null
    }


    override fun onDestroyView() {
        super.onDestroyView()
        updateJob?.cancel()
    }

    override fun onPause() {
        super.onPause()
        updateJob?.cancel()
    }

    private fun handleFileUri(uri: Uri) {
        val file = uriToFile(uri, requireContext())
        if(file != null) {
            uiScope.launch {
                val response = async(Dispatchers.IO) { retrofitService.uploadFile(file) }
                withContext(Dispatchers.IO) {
                    // костыль чтобы отображалось корректное имя файла - кладу его в voice
                    retrofitService.sendMessage(dialog.id, null, null, file.name, response.await())
                }
                countMsg += 1
                adapter.messages =
                    retrofitService.getMessages(dialog.id, 0, countMsg).associateWith { "" }
                binding.recyclerview.post {
                    binding.recyclerview.scrollToPosition(adapter.itemCount - 1)
                }
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

    private fun formatUserSessionDate(timestamp: Long?): String {
        if (timestamp == null) return "Никогда не был в сети"

        // Приведение серверного времени (МСК GMT+3) к GMT
        val greenwichSessionDate = Calendar.getInstance().apply {
            timeInMillis = timestamp - 10800000
        }
        val now = Calendar.getInstance()

        val diffInMillis = now.timeInMillis - greenwichSessionDate.timeInMillis
        val diffInMinutes = (diffInMillis / 60000).toInt()

        val dateFormatTime = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormatDayMonth = SimpleDateFormat("d MMM", Locale.getDefault())
        val dateFormatYear = SimpleDateFormat("d.MM.yyyy", Locale.getDefault())

        return when {
            diffInMinutes < 2 -> "в сети"
            diffInMinutes < 5 -> "был в сети только что"
            diffInMinutes < 60 -> "был в сети $diffInMinutes минут назад"
            diffInMinutes < 120 -> "был в сети час назад"
            diffInMinutes < 180 -> "был в сети два часа назад"
            diffInMinutes < 240 -> "был в сети три часа назад"
            diffInMinutes < 1440 -> "был в сети в ${dateFormatTime.format(greenwichSessionDate.time)}"
            else -> {
                // Проверка года
                val currentYear = now.get(Calendar.YEAR)
                val sessionYear = greenwichSessionDate.get(Calendar.YEAR)
                if (currentYear == sessionYear) {
                    "был в сети ${dateFormatDayMonth.format(greenwichSessionDate.time)}"
                } else {
                    "был в сети ${dateFormatYear.format(greenwichSessionDate.time)}"
                }
            }
        }
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
                        replaceFragment(MessageFragment(dialog))
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
                            if (!s.isNullOrEmpty() && s.length >= 2) {
                                searchMessages(s)
                            }
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

    private fun showPopupMenuMessage(view: View, menuRes: Int, message: Message, localMedias: ArrayList<LocalMedia>?) {
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
                                startMessagePolling()
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
                            stopMessagePolling()
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
                                                retrofitService.uploadPhoto(file)
                                            }
                                            list += tmp.await()
                                        } else {
                                            val tmp = async(Dispatchers.IO) {
                                                retrofitService.uploadPhoto(File(uItem.availablePath))
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
                                                retrofitService.deleteFile("photos", file.name)
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
                                            retrofitService.editMessage(
                                                message.id,
                                                text,
                                                finalList,
                                                null,
                                                null
                                            )
                                        } else
                                            retrofitService.editMessage(
                                                message.id,
                                                null,
                                                finalList,
                                                null,
                                                null
                                            )
                                    }
                                    if (resp.await()) {
                                        editFlag = false
                                        imageAdapter.clearImages()
                                        editText.setText("")
                                        editButton.visibility = View.GONE
                                        binding.recordView.visibility = View.VISIBLE
                                        startMessagePolling()
                                    }
                                }
                            } else {
                                if (text.isNotEmpty()) {
                                    val resp = async(Dispatchers.IO) {
                                        retrofitService.editMessage(
                                            message.id,
                                            text,
                                            arrayListOf(),
                                            null,
                                            null
                                        )
                                    }
                                    if (resp.await()) {
                                        editFlag = false
                                        imageAdapter.clearImages()
                                        editText.setText("")
                                        editButton.visibility = View.GONE
                                        binding.recordView.visibility = View.VISIBLE
                                        startMessagePolling()
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
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun replaceFragment(newFragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, newFragment)
            .commit()
    }

    private fun searchMessages(query: CharSequence?) {
        uiScope.launch {
            if (query.isNullOrEmpty()) {
                adapter.messages = retrofitService.getMessages(dialog.id, 0, countMsg).associateWith { "" }
            } else {
                adapter.messages = retrofitService.searchMessagesInDialog(dialog.id, query.toString()).associateWith { "" }
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
