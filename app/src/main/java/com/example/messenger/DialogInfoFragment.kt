package com.example.messenger

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
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
            // todo load images/other
        }
        val dialogInfoAdapter = DialogInfoAdapter()
        binding.recyclerview.adapter = dialogInfoAdapter
        // GridLayoutManager для медиа (фото и видео по 3 в ряд), LinearLayoutManager для файлов и аудио
        val gridLayoutManager = GridLayoutManager(requireContext(), 3).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return when (dialogInfoAdapter.getItemViewType(position)) {
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
                if (layoutManager != null && layoutManager.findLastVisibleItemPosition() == dialogInfoAdapter.itemCount - 1) {
                    // Достигнут конец списка, подгружаем новые элементы
                    loadMoreMediaItems()
                }
            }
        })
        binding.recyclerview.layoutManager = gridLayoutManager
        return binding.root
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
}