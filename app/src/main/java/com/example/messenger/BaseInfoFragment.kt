package com.example.messenger

import android.app.AlertDialog
import android.content.Context
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.messenger.databinding.FragmentDialogInfoBinding
import com.example.messenger.model.MediaItem
import com.example.messenger.model.User
import com.example.messenger.picker.CustomPreviewFragment
import com.example.messenger.picker.FilePickerManager
import com.example.messenger.picker.GlideEngine
import com.luck.picture.lib.basic.PictureSelector
import com.luck.picture.lib.config.InjectResourceSource
import com.luck.picture.lib.entity.LocalMedia
import com.luck.picture.lib.interfaces.OnInjectLayoutResourceListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
abstract class BaseInfoFragment : Fragment() {
    protected lateinit var binding: FragmentDialogInfoBinding
    private lateinit var adapter: DialogInfoAdapter
    protected lateinit var filePickerManager: FilePickerManager
    protected var selectedType: Int = 0
    protected var currentPage: Int = 0
    protected var isCanDoPagination: Boolean = true
    private var isPaginationAllowed: Boolean = true
    protected var colorAccent: Int = 0
    protected var colorPrimary: Int = 0
    protected var fileUpdate: File? = null

    protected val viewModel: BaseInfoViewModel by viewModels()

    abstract fun getAvatarString(): String
    abstract fun getUpperName(): String
    abstract fun getIsOwner(): Boolean
    abstract fun getCanDelete(): Boolean
    abstract fun getInterval(): Int
    abstract fun getMembers(): List<User>
    abstract fun getCurrentUserId(): Int
    abstract fun getGroupOwnerId(): Int
    abstract fun deleteUserFromGroup(user: User)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().window.statusBarColor = ContextCompat.getColor(requireContext(), R.color.colorBar)
        val toolbarContainer: FrameLayout = view.findViewById(R.id.toolbar_container)
        val defaultToolbar = LayoutInflater.from(context)
            .inflate(R.layout.toolbar_info, toolbarContainer, false)
        toolbarContainer.addView(defaultToolbar)
        val backArrow: ImageView = view.findViewById(R.id.back_arrow)
        backArrow.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        val options: ImageView = view.findViewById(R.id.ic_options)
        options.setOnClickListener {
            showPopupMenu(it, R.menu.dialog_info_menu)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDialogInfoBinding.inflate(inflater, container, false)
        filePickerManager = FilePickerManager(fragment3 =  this)
        val typedValue = TypedValue()
        context?.theme?.resolveAttribute(android.R.attr.colorAccent, typedValue, true)
        colorAccent = typedValue.data
        context?.theme?.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
        colorPrimary = typedValue.data
        lifecycleScope.launch {
            val avatar = getAvatarString()
            if (avatar != "") {
                binding.progressBar.visibility = View.VISIBLE
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
                        fileUpdate = file
                        if (!second) viewModel.fManagerSaveAvatar(avatar, file.readBytes())
                        val uri = Uri.fromFile(file)
                        binding.photoImageView.imageTintList = null
                        Glide.with(requireContext())
                            .load(uri)
                            .apply(RequestOptions.centerCropTransform())
                            .placeholder(R.color.app_color_f6)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(binding.photoImageView)
                        binding.progressBar.visibility = View.GONE
                    } else {
                        binding.progressBar.visibility = View.GONE
                        binding.errorImageView.visibility = View.VISIBLE
                    }
                } else {
                    binding.progressBar.visibility = View.GONE
                    binding.errorImageView.visibility = View.VISIBLE
                }
            }
        }
        binding.userNameTextView.text = getUpperName()
        lifecycleScope.launch {
            binding.switchNotifications.isChecked = viewModel.isNotificationsEnabled()
        }
        if(getIsOwner()) {
            binding.switchDelete.setOnClickListener {
                binding.switchDelete.isEnabled = false
                lifecycleScope.launch {
                    viewModel.toggleCanDeleteDialog()
                    delay(5000)
                    binding.switchDelete.isEnabled = true
                }
            }
        } else binding.switchDelete.isEnabled = false
        binding.switchDelete.isChecked = getCanDelete()
        binding.switchNotifications.setOnClickListener {
            binding.switchNotifications.isEnabled = false
            lifecycleScope.launch {
                viewModel.turnNotifications()
                delay(5000)
                binding.switchNotifications.isEnabled = true
            }
        }
        binding.loadButton.setOnClickListener {
            loadMoreMediaItems(selectedType, currentPage) {}
        }
        binding.buttonMedia.setOnClickListener {
            if(selectedType != MediaItem.TYPE_MEDIA) {
                loadMoreMediaItems(0, 0) { success ->
                    if(success) {
                        binding.buttonMedia.setTextColor(colorPrimary)
                        binding.buttonFiles.setTextColor(colorAccent)
                        binding.buttonAudio.setTextColor(colorAccent)
                        binding.buttonMembers.setTextColor(colorAccent)
                        selectedType = 0
                        currentPage = 1
                        isCanDoPagination = true
                    }
                }
            }
        }
        binding.buttonFiles.setOnClickListener {
            loadMoreMediaItems(1, 0) { success ->
                if(success) {
                    if(selectedType != MediaItem.TYPE_FILE) {
                        binding.buttonMedia.setTextColor(colorAccent)
                        binding.buttonFiles.setTextColor(colorPrimary)
                        binding.buttonAudio.setTextColor(colorAccent)
                        binding.buttonMembers.setTextColor(colorAccent)
                        selectedType = 1
                        currentPage = 1
                        isCanDoPagination = true
                    }
                }
            }
        }
        binding.buttonAudio.setOnClickListener {
            if(selectedType != MediaItem.TYPE_AUDIO) {
                loadMoreMediaItems(2, 0) { success ->
                    if(success) {
                        binding.buttonMedia.setTextColor(colorAccent)
                        binding.buttonFiles.setTextColor(colorAccent)
                        binding.buttonAudio.setTextColor(colorPrimary)
                        binding.buttonMembers.setTextColor(colorAccent)
                        selectedType = 2
                        currentPage = 1
                        isCanDoPagination = true
                    }
                }
            }
        }
        binding.photoImageView.setOnClickListener {
            val fileTemp = fileUpdate
            if(fileTemp != null)
                openPictureSelector(filePickerManager, viewModel.fileToLocalMedia(fileTemp))
        }
        val displayMetrics = context?.resources?.displayMetrics
        val imageSize: Int
        val spacing = 3
        imageSize = if(displayMetrics != null) (displayMetrics.widthPixels - spacing * 4) / 3 else 100
        adapter = DialogInfoAdapter(requireContext(), imageSize, viewModel, getCurrentUserId(), getGroupOwnerId(), object: DialogActionListener {
            override fun onItemClicked(position: Int, localMedias: ArrayList<LocalMedia>) {
                PictureSelector.create(requireActivity())
                    .openPreview()
                    .setImageEngine(GlideEngine.createGlideEngine())
                    //.setVideoPlayerEngine(ExoPlayerEngine())
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
                    .setInjectActivityPreviewFragment {
                        CustomPreviewFragment.newInstance(viewModel)
                    }
                    .startActivityPreview(position, false, localMedias)
            }

            override fun onUserDeleteClicked(user: User) {
                deleteUserFromGroup(user)
            }
        })
        binding.recyclerview.adapter = adapter
        binding.recyclerview.addItemDecoration(GridSpacingItemDecoration(3, spacing, true))
        // GridLayoutManager для медиа (фото и видео по 3 в ряд), LinearLayoutManager для файлов и аудио
        val gridLayoutManager = GridLayoutManager(requireContext(), 3).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return when (adapter.getItemViewType(position)) {
                        MediaItem.TYPE_MEDIA -> 1 // Медиа по одному элементу
                        MediaItem.TYPE_FILE, MediaItem.TYPE_AUDIO, MediaItem.TYPE_USER -> 3 // Файлы и аудио занимают всю строку
                        else -> 1
                    }
                }
            }
        }
        // Пагинация
        binding.recyclerview.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as? GridLayoutManager
                if (layoutManager != null && layoutManager.findLastVisibleItemPosition() == adapter.itemCount - 1
                    && adapter.itemCount > 5 && isCanDoPagination && isPaginationAllowed) {
                    Log.d("testTryPagination", "page : $currentPage")
                    // Достигнут конец списка, подгружаем новые элементы
                    loadMoreMediaItems(selectedType, currentPage) {}
                    isPaginationAllowed = false
                    lifecycleScope.launch {
                        delay(3000)
                        isPaginationAllowed = true
                    }
                }
            }
        })
        binding.recyclerview.layoutManager = gridLayoutManager
        return binding.root
    }

    protected fun loadMoreMediaItems(type: Int, page: Int, callback: (Boolean) -> Unit) {
        val context = requireContext()
        when(type) {
            MediaItem.TYPE_MEDIA -> {
                lifecycleScope.launch {
                    val list = viewModel.getMediaPreviews(page)
                    if(!list.isNullOrEmpty()) {
                        val items = async {
                            val tmp = mutableListOf<String>()
                            list.forEach {
                                tmp.add(viewModel.getPreview(context, it))
                            }
                            return@async tmp
                        }
                        adapter.addMediaItems(MediaItem.TYPE_MEDIA, items.await().map { MediaItem(type, it) })
                        binding.loadButton.visibility = View.GONE
                        currentPage++
                        callback(true)
                    } else {
                        if(list == null) Toast.makeText(requireContext(), "Ошибка: Нет сети!", Toast.LENGTH_SHORT).show()
                        else Toast.makeText(requireContext(), "Медиа файлов нет", Toast.LENGTH_SHORT).show()
                        if(currentPage == 0) callback(false) else isCanDoPagination = false
                    }
                }
            }
            MediaItem.TYPE_FILE -> {
                lifecycleScope.launch {
                    val list = viewModel.getFiles(page)
                    if(!list.isNullOrEmpty()) {
                        adapter.addMediaItems(MediaItem.TYPE_FILE, list.map { MediaItem(type, it) })
                        currentPage++
                        callback(true)
                    } else {
                        if(list == null) Toast.makeText(requireContext(), "Ошибка: Нет сети!", Toast.LENGTH_SHORT).show()
                        else Toast.makeText(requireContext(), "Файлов нет", Toast.LENGTH_SHORT).show()
                        if(currentPage == 0) callback(false) else isCanDoPagination = false
                    }
                    binding.loadButton.visibility = View.GONE
                }
            }
            MediaItem.TYPE_AUDIO -> {
                lifecycleScope.launch {
                    val list = viewModel.getAudios(page)
                    if(!list.isNullOrEmpty()) {
                        adapter.addMediaItems(MediaItem.TYPE_AUDIO, list.map { MediaItem(type, it) })
                        currentPage++
                        callback(true)
                    } else {
                        if(list == null) Toast.makeText(requireContext(), "Ошибка: Нет сети!", Toast.LENGTH_SHORT).show()
                        else Toast.makeText(requireContext(), "Голосовых нет", Toast.LENGTH_SHORT).show()
                        if(currentPage == 0) callback(false) else isCanDoPagination = false
                    }
                    binding.loadButton.visibility = View.GONE
                }
            }
            MediaItem.TYPE_USER -> {
                val members = getMembers()
                if(members.isNotEmpty()) {
                    adapter.addMediaItems(MediaItem.TYPE_USER, members.map { MediaItem(type, "", it)})
                    callback(true)
                } else {
                    callback(false)
                    isCanDoPagination = false
                }
            }
        }
    }

    class GridSpacingItemDecoration(private val spanCount: Int, private val spacing: Int, private val includeEdge: Boolean) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State
        ) {
            val position = parent.getChildAdapterPosition(view)
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

    private fun showPopupMenu(view: View, menuRes: Int) {
        val popupMenu = PopupMenu(requireContext(), view)
        popupMenu.menuInflater.inflate(menuRes, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.auto_delete -> {
                    showNumberPickerDialog()
                    true
                }
                R.id.delete_all_messages -> {
                    showConfirmDeleteMessagesDialog()
                    true
                }
                R.id.delete_dialog -> {
                    showConfirmDeleteChatDialog()
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun showConfirmDeleteMessagesDialog() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Вы уверены, что хотите удалить все сообщения?")
            .setPositiveButton("Удалить") { dialogInterface, _ ->
                dialogInterface.dismiss()
                lifecycleScope.launch {
                    val success = viewModel.deleteAllMessages()
                    if(success) requireActivity().onBackPressedDispatcher.onBackPressed()
                    else Toast.makeText(requireContext(), "Ошибка: Нет сети!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Назад") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()

        dialog.show()
    }

    private fun showConfirmDeleteChatDialog() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Вы уверены, что хотите удалить этот чат?")
            .setPositiveButton("Удалить") { dialogInterface, _ ->
                dialogInterface.dismiss()
                lifecycleScope.launch {
                    val (success, message) = viewModel.deleteConv()
                    if(success) {
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragmentContainer, MessengerFragment(), "MESSENGER_FRAGMENT_FROM_DIALOG_TAG")
                            .commit()
                    } else Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Назад") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()

        dialog.show()
    }

    private fun showNumberPickerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.delete_interval_dialog, null)
        val numberPicker = dialogView.findViewById<NumberPicker>(R.id.numberPicker)

        // Настраиваем список значений
        val values = arrayOf("Выключено", "Каждые 30 секунд", "Каждую минуту", "Каждые 5 минут", "Каждые 10 минут")
        numberPicker.minValue = 0
        numberPicker.maxValue = values.size - 1
        numberPicker.displayedValues = values
        val numb = when(getInterval()) {
            0 -> 0
            30 -> 1
            60 -> 2
            300 -> 3
            600 -> 4
            else -> 0
        }
        numberPicker.value = numb
        // Создаем диалог
        AlertDialog.Builder(requireActivity())
            .setTitle("Автоудаление сообщений")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                // Обработка выбранного значения
                val selectedValue = values[numberPicker.value]
                if(numberPicker.value != numb) {
                    val interval = when(numberPicker.value) {
                        0 -> 0
                        1 -> 30
                        2 -> 60
                        3 -> 300
                        4 -> 600
                        else -> 0
                    }
                    lifecycleScope.launch {
                        val success = viewModel.updateAutoDeleteInterval(interval)
                        if(success) Toast.makeText(requireContext(), "Установлено автоудаление: $selectedValue", Toast.LENGTH_SHORT).show()
                        else Toast.makeText(requireContext(), "Ошибка: Нет сети!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openPictureSelector(filePickerManager: FilePickerManager, localMedia: LocalMedia) {
        PictureSelector.create(requireActivity())
            .openPreview()
            .setImageEngine(GlideEngine.createGlideEngine())
            //.setVideoPlayerEngine(ExoPlayerEngine())
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
            .startActivityPreview(0, false, arrayListOf(localMedia))
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.clearTempFiles()
    }
}