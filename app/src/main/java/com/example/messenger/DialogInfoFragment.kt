package com.example.messenger

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.NumberPicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.messenger.databinding.FragmentDialogInfoBinding
import com.example.messenger.model.Dialog
import com.example.messenger.model.MediaItem
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.User
import com.example.messenger.picker.CustomPreviewFragment
import com.example.messenger.picker.ExoPlayerEngine
import com.example.messenger.picker.FilePickerManager
import com.example.messenger.picker.GlideEngine
import com.luck.picture.lib.PictureSelectorPreviewFragment
import com.luck.picture.lib.basic.PictureSelector
import com.luck.picture.lib.config.InjectResourceSource
import com.luck.picture.lib.entity.LocalMedia
import com.luck.picture.lib.interfaces.OnExternalPreviewEventListener
import com.luck.picture.lib.interfaces.OnInjectActivityPreviewListener
import com.luck.picture.lib.interfaces.OnInjectLayoutResourceListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DialogInfoFragment(
    private val dialog: Dialog,
    private val lastSessionString : String,
    private val messageViewModel: MessageViewModel
) : Fragment() {
    private lateinit var binding: FragmentDialogInfoBinding
    private lateinit var preferences: SharedPreferences
    private lateinit var adapter: DialogInfoAdapter
    private var selectedType: Int = 0
    private var currentPage: Int = 0
    private var isCanDoPagination: Boolean = true
    private var isPaginationAllowed: Boolean = true
    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Настройка Toolbar
        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        (activity as AppCompatActivity).setSupportActionBar(toolbar)
        @Suppress("DEPRECATION")
        setHasOptionsMenu(true) // Включить меню для этого фрагмента

        // Настройка кнопки "назад"
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDialogInfoBinding.inflate(inflater, container, false)
        preferences = requireContext().getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE)
        val wallpaper = preferences.getString(PREF_WALLPAPER, "")
        if(wallpaper != "") {
            val resId = resources.getIdentifier(wallpaper, "drawable", requireContext().packageName)
            if(resId != 0)
                binding.dialogInfoLayout.background = ContextCompat.getDrawable(requireContext(), resId)
        }
        val filePickerManager = FilePickerManager(null, null, this)
        val typedValue = TypedValue()
        context?.theme?.resolveAttribute(android.R.attr.colorAccent, typedValue, true)
        val colorAccent = typedValue.data
        context?.theme?.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
        val colorPrimary = typedValue.data
        uiScope.launch {
            val avatar = dialog.otherUser.avatar ?: ""
            if (avatar != "") {
                withContext(Dispatchers.Main) { binding.progressBar.visibility = View.VISIBLE }
                val filePathTemp = async(Dispatchers.IO) {
                    if (messageViewModel.fManagerIsExist(avatar)) {
                        return@async Pair(messageViewModel.fManagerGetFilePath(avatar), true)
                    } else {
                        try {
                            return@async Pair(messageViewModel.downloadFile(requireContext(), "photos", avatar), false)
                        } catch (e: Exception) {
                            return@async Pair(null, true)
                        }
                    }
                }
                val (first, second) = filePathTemp.await()
                if (first != null) {
                    val file = File(first)
                    if (file.exists()) {
                        if (!second) messageViewModel.fManagerSaveFile(avatar, file.readBytes())
                        val uri = Uri.fromFile(file)
                        Glide.with(requireContext())
                            .load(uri)
                            .apply(RequestOptions.circleCropTransform())
                            .placeholder(R.color.app_color_f6)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(binding.photoImageView)
                        binding.progressBar.visibility = View.GONE
                    } else {
                        withContext(Dispatchers.Main) {
                            binding.progressBar.visibility = View.GONE
                            binding.errorImageView.visibility = View.VISIBLE
                        }
                    }
                } else {
                    binding.progressBar.visibility = View.GONE
                    binding.errorImageView.visibility = View.VISIBLE
                }
            }
        }
        binding.userNameTextView.text = dialog.otherUser.username
        binding.lastSessionTextView.text = lastSessionString
        binding.nickTextView.text = dialog.otherUser.name
        uiScope.launch {
            binding.switchNotifications.isChecked = messageViewModel.isNotificationsEnabled(dialog.id)
        }
        binding.switchDelete.isChecked = dialog.canDelete
        binding.copyImageView.setOnClickListener {
            val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("label", dialog.otherUser.name)
            clipboard.setPrimaryClip(clip)
        }
        binding.switchNotifications.setOnClickListener {
            binding.switchNotifications.isEnabled = false
            uiScope.launch {
                messageViewModel.turnNotifications(dialog.id)
                delay(5000)
                binding.switchNotifications.isEnabled = true
            }
        }
        binding.switchDelete.setOnClickListener {
            binding.switchDelete.isEnabled = false
            uiScope.launch {
                messageViewModel.toggleCanDeleteDialog(dialog.id)
                delay(5000)
                binding.switchDelete.isEnabled = true
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
                        selectedType = 0
                        currentPage = 1
                        isCanDoPagination = true
                    }
                }
            }
        }
        binding.buttonFiles.setOnClickListener {
            if(selectedType != MediaItem.TYPE_FILE) {
                loadMoreMediaItems(1, 0) { success ->
                    if(success) {
                        binding.buttonMedia.setTextColor(colorAccent)
                        binding.buttonFiles.setTextColor(colorPrimary)
                        binding.buttonAudio.setTextColor(colorAccent)
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
                        selectedType = 2
                        currentPage = 1
                        isCanDoPagination = true
                    }
                }
            }
        }
        val displayMetrics = context?.resources?.displayMetrics
        val imageSize: Int
        val spacing = 3
        imageSize = if(displayMetrics != null) (displayMetrics.widthPixels - spacing * 4) / 3 else 100
        adapter = DialogInfoAdapter(requireContext(), imageSize, messageViewModel, object: DialogActionListener {
            override fun onItemClicked(position: Int, localMedias: ArrayList<LocalMedia>) {
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
                    .setInjectActivityPreviewFragment {
                        CustomPreviewFragment.newInstance(messageViewModel)
                    }
                    .startActivityPreview(position, false, localMedias)
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
                        MediaItem.TYPE_FILE, MediaItem.TYPE_AUDIO -> 3 // Файлы и аудио занимают всю строку
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
                    uiScope.launch {
                        delay(3000)
                        isPaginationAllowed = true
                    }
                }
            }
        })
        binding.recyclerview.layoutManager = gridLayoutManager
        return binding.root
    }

    private fun loadMoreMediaItems(type: Int, page: Int, callback: (Boolean) -> Unit) {
        val context = requireContext()
        when(type) {
            MediaItem.TYPE_MEDIA -> {
                uiScope.launch {
                    val list = messageViewModel.getMediaPreviews(page)
                    if(!list.isNullOrEmpty()) {
                        val items = async(Dispatchers.IO) {
                            val tmp = mutableListOf<String>()
                            list.forEach {
                                tmp.add(messageViewModel.getPreview(context, it))
                            }
                            return@async tmp
                        }
                        adapter.addMediaItems(MediaItem.TYPE_MEDIA, items.await().map { MediaItem(type, it) })
                        binding.loadButton.visibility = View.GONE
                        currentPage++
                        callback(true)
                    } else {
                        Toast.makeText(requireContext(), "Медиа файлов нет", Toast.LENGTH_SHORT).show()
                        if(currentPage == 0) callback(false) else isCanDoPagination = false
                    }
                }
            }
            MediaItem.TYPE_FILE -> {
                uiScope.launch {
                    val list = messageViewModel.getFiles(page)
                    if(!list.isNullOrEmpty()) {
                        adapter.addMediaItems(MediaItem.TYPE_FILE, list.map { MediaItem(type, it) })
                        currentPage++
                        callback(true)
                    } else {
                        Toast.makeText(requireContext(), "Файлов нет", Toast.LENGTH_SHORT).show()
                        if(currentPage == 0) callback(false) else isCanDoPagination = false
                    }
                    binding.loadButton.visibility = View.GONE
                }
            }
            MediaItem.TYPE_AUDIO -> {
                uiScope.launch {
                    val list = messageViewModel.getAudios(page)
                    if(!list.isNullOrEmpty()) {
                        adapter.addMediaItems(MediaItem.TYPE_AUDIO, list.map { MediaItem(type, it) })
                        currentPage++
                        callback(true)
                    } else {
                        Toast.makeText(requireContext(), "Голосовых нет", Toast.LENGTH_SHORT).show()
                        if(currentPage == 0) callback(false) else isCanDoPagination = false
                    }
                    binding.loadButton.visibility = View.GONE
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

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.dialog_info_menu, menu)
    }


    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.auto_delete -> {
                showNumberPickerDialog()
                true
            }
            R.id.delete_all_messages -> {
                uiScope.launch {
                    messageViewModel.deleteAllMessages(dialog.id)
                }
                requireActivity().onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.delete_dialog -> {
                uiScope.launch {
                    messageViewModel.deleteDialog(dialog.id)
                }
                parentFragmentManager.beginTransaction()
                    .replace(
                        R.id.fragmentContainer,
                        MessengerFragment(),
                        "MESSENGER_FRAGMENT_FROM_DIALOG_TAG"
                    )
                    .commit()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showNumberPickerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.delete_interval_dialog, null)
        val numberPicker = dialogView.findViewById<NumberPicker>(R.id.numberPicker)

        // Настраиваем список значений
        val values = arrayOf("Выключено", "Каждые 30 секунд", "Каждую минуту", "Каждые 5 минут", "Каждые 10 минут")
        numberPicker.minValue = 0
        numberPicker.maxValue = values.size - 1
        numberPicker.displayedValues = values
        val numb = when(dialog.autoDeleteInterval) {
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
                Toast.makeText(requireContext(), "Установлено автоудаление: $selectedValue", Toast.LENGTH_SHORT).show()
                if(numberPicker.value != numb) {
                    val interval = when(numberPicker.value) {
                        0 -> 0
                        1 -> 30
                        2 -> 60
                        3 -> 300
                        4 -> 600
                        else -> 0
                    }
                    uiScope.launch {
                        messageViewModel.updateAutoDeleteInterval(interval)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        messageViewModel.clearTempFiles()
    }
}