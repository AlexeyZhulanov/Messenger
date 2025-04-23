package com.example.messenger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.messenger.databinding.FragmentSettingsBinding
import com.example.messenger.model.User
import com.example.messenger.picker.ExoPlayerEngine
import com.example.messenger.picker.FilePickerManager
import com.example.messenger.picker.GlideEngine
import com.luck.picture.lib.basic.PictureSelector
import com.luck.picture.lib.config.InjectResourceSource
import com.luck.picture.lib.entity.LocalMedia
import com.luck.picture.lib.interfaces.OnInjectLayoutResourceListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class SettingsFragment(
    private val initialUser: User
) : Fragment() {
    private lateinit var binding: FragmentSettingsBinding
    private lateinit var adapterWallpaper: WallpaperAdapter
    private lateinit var adapterColorTheme: ColorThemeMenuAdapter
    private val viewModel: SettingsViewModel by viewModels()
    private var fileUpdate: File? = null
    private var currentUser: User? = null
    private val alf = ('a'..'z') + ('A'..'Z') + ('0'..'9') + ('А'..'Я') + ('а'..'я') + ('!') + ('$') + (' ')

    private val colorThemeItems = listOf(
        ColorThemeMenuItem(R.color.colorPrimary, R.color.colorMessageSenderBack, 1, false),
        ColorThemeMenuItem(R.color.colorPrimary2, R.color.chatColor2, 2, false),
        ColorThemeMenuItem(R.color.colorPrimary3, R.color.chatColor3, 3, false)
    )

    private val wallpaperLightItems = listOf(
        WallpaperMenuItem(R.color.colorDefault2, "", 1, false),
        WallpaperMenuItem(R.drawable.wallpaper6, "wallpaper6", 2, false)
    )

    private val wallpaperDarkItems = listOf(
        WallpaperMenuItem(R.color.colorDefault2, "", 1, false),
        WallpaperMenuItem(R.drawable.wallpaper1, "wallpaper1", 2, false)
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().window.statusBarColor = ContextCompat.getColor(requireContext(), R.color.colorBar)
        requireActivity().window.navigationBarColor = ContextCompat.getColor(requireContext(), R.color.navigation_bar_color)
        viewModel.wallpaper.observe(viewLifecycleOwner) { wallpaper ->
            if(isDarkTheme(requireContext())) {
                wallpaperDarkItems.forEach {
                    it.isChecked = wallpaper == it.wallpaperName
                }
                adapterWallpaper.items = wallpaperDarkItems
            } else {
                wallpaperLightItems.forEach {
                    it.isChecked = wallpaper == it.wallpaperName
                }
                adapterWallpaper.items = wallpaperLightItems
            }
            if (wallpaper != "") {
                val resId = WALLPAPER_MAP[wallpaper] ?: -1
                if (resId != -1) binding.settingsLayout.background = ContextCompat.getDrawable(requireContext(), resId)
            } else binding.settingsLayout.background = null
        }
        viewModel.themeNumber.observe(viewLifecycleOwner) { themeNumber ->
            colorThemeItems.forEach { it.isChecked = false }
            val idx = if(themeNumber == 0) 0 else (themeNumber - 1)
            colorThemeItems[idx].isChecked = true
            adapterColorTheme.menuItems = colorThemeItems
        }
        viewModel.currentUser.observe(viewLifecycleOwner) { user ->
            currentUser = user
            binding.usernameTextView.text = user.username
            binding.nameTextView.text = user.name
            lifecycleScope.launch {
                val avatar = user.avatar ?: ""
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
                                .apply(RequestOptions.circleCropTransform())
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
            binding.editUsernameButton.setOnClickListener {
                showAddDialog(user.username)
            }
            binding.copyNameButton.setOnClickListener {
                val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("label", user.name)
                clipboard.setPrimaryClip(clip)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentSettingsBinding.inflate(inflater, container, false)
        viewModel.setUser(initialUser)
        val filePickerManager = FilePickerManager(fragment2 = this)

        binding.editPhotoButton.setOnClickListener {
            showPopupMenu(it, R.menu.popup_menu_user, filePickerManager, fileUpdate)
        }
        binding.photoImageView.setOnClickListener {
            if(fileUpdate != null)
            openPictureSelector(filePickerManager, viewModel.fileToLocalMedia(fileUpdate))
        }
        binding.changePassword.setOnClickListener {
            BottomSheetPasswordFragment(viewModel, object : BottomSheetListener {
                override fun onChangePassword() {
                    Toast.makeText(requireContext(), "Пароль успешно обновлен", Toast.LENGTH_SHORT)
                        .show()
                }
            }).show(childFragmentManager, "PasswordChange")

        }
        binding.clearFiles.setOnClickListener {
            showConfirmClearFilesDialog()
        }
        binding.logoutButton.setOnClickListener {
            showConfirmLogoutDialog()
        }

        setupWallpaperAdapter()
        binding.wallpaperRecyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.wallpaperRecyclerView.addItemDecoration(HorizontalMarginItemDecoration(50))
        binding.wallpaperRecyclerView.adapter = adapterWallpaper

        setupColorThemeAdapter()
        binding.themeRecyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.themeRecyclerView.addItemDecoration(HorizontalMarginItemDecoration(50))
        binding.themeRecyclerView.adapter = adapterColorTheme

        return binding.root
    }

    private fun setupColorThemeAdapter() {
        adapterColorTheme = ColorThemeMenuAdapter { item ->
            viewModel.updateTheme(item.themeNumber)
        }
    }

    private fun setupWallpaperAdapter() {
        adapterWallpaper = WallpaperAdapter { item ->
            viewModel.updateWallpaper(item.wallpaperName)
        }
    }

    private fun isDarkTheme(context: Context): Boolean {
        return when (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
    }

    private fun showPopupMenu(view: View, menuRes: Int, filePickerManager: FilePickerManager, file: File?) {
        val popupMenu = PopupMenu(requireContext(), view)
        popupMenu.menuInflater.inflate(menuRes, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.item_delete -> {
                    if(currentUser?.avatar != null) {
                        lifecycleScope.launch {
                            val success = viewModel.updateAvatar("delete")
                            if(success) {
                                viewModel.updateAvatarValue("")
                                Toast.makeText(requireContext(), "Аватарка успешно удалена, чтобы увидеть, выйдите с этой страницы", Toast.LENGTH_SHORT).show()
                            } else Toast.makeText(requireContext(), "Ошибка: Нет сети!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(requireContext(), "Аватарки и так нет!", Toast.LENGTH_SHORT).show()
                    }
                    true
                }

                R.id.item_update -> {
                    if (file != null) {
                    lifecycleScope.launch {
                        val res = async { filePickerManager.openFilePicker(isCircle = true, isFreeStyleCrop = false, arrayListOf(viewModel.fileToLocalMedia(file))) }
                        val photo = res.await()
                        if(photo.isNotEmpty()) {
                            val path = viewModel.uploadAvatar(File(photo[0].availablePath))
                            if(path != "") {
                                val success = viewModel.updateAvatar(path)
                                if(success) {
                                    viewModel.updateAvatarValue(path)
                                } else Toast.makeText(requireContext(), "Ошибка: Нет сети!", Toast.LENGTH_SHORT).show()
                            } else Toast.makeText(requireContext(), "Ошибка: Нет сети!", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(requireContext(), "Нельзя редактировать пустоту!", Toast.LENGTH_SHORT).show()
                    }
                    true
                }

                R.id.item_new -> {
                    lifecycleScope.launch {
                        val res = async { filePickerManager.openFilePicker(isCircle = true, isFreeStyleCrop = false, arrayListOf()) }
                        val photo = res.await()
                        if(photo.isNotEmpty()) {
                            val path = viewModel.uploadAvatar(File(photo[0].availablePath))
                            if(path != "") {
                                val success = viewModel.updateAvatar(path)
                                if(success) {
                                    viewModel.updateAvatarValue(path)
                                } else Toast.makeText(requireContext(), "Ошибка: Нет сети!", Toast.LENGTH_SHORT).show()
                            } else Toast.makeText(requireContext(), "Ошибка: Нет сети!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun showAddDialog(username: String) {
        // Inflate the custom layout for the dialog
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_item, null)
        val editText = dialogView.findViewById<EditText>(R.id.dialog_input)
        editText.setText(username)
        // Create the AlertDialog
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Изменить") { dialogInterface, _ ->
                lifecycleScope.launch {
                    val input = editText.text.toString()
                    input.forEach {
                        if(it !in alf) {
                            dialogInterface.dismiss()
                            Toast.makeText(requireContext(), "Недопустимые символы в нике", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                    }
                    val success = viewModel.updateUserName(input)
                    if(success) viewModel.updateUsernameValue(input)
                    else Toast.makeText(requireContext(), "Ошибка: Нет сети!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Назад") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()

        dialog.show()
    }

    private fun showConfirmLogoutDialog() {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Вы уверены, что хотите выйди из аккаунта?")
            .setPositiveButton("Выйти") { dialogInterface, _ ->
                logout()
                dialogInterface.dismiss()
            }
            .setNegativeButton("Назад") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()

        dialog.show()
    }

    private fun showConfirmClearFilesDialog() {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Вы уверены, что хотите удалить все файлы?")
            .setMessage("Файлы будут загружаться заново по мере их появления на экране")
            .setPositiveButton("Удалить") { dialogInterface, _ ->
                viewModel.clearAllAppFiles { success ->
                    dialogInterface.dismiss()
                    val msg = if(success) "Все файлы успешно удалены" else "Ошибка: Файлы не удалось удалить"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Назад") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()

        dialog.show()
    }

    private fun openPictureSelector(filePickerManager: FilePickerManager, localMedia: LocalMedia) {
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
            .startActivityPreview(0, false, arrayListOf(localMedia))
    }

    private fun logout() {
        lifecycleScope.launch {
            val success = viewModel.deleteFCMToken()
            if(success) {
                viewModel.clearCurrentUser()
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, LoginFragment(), "LOGIN_FRAGMENT_TAG3")
                    .commit()
            } else Toast.makeText(requireContext(), "Не удалось выйти из аккаунта, нет сети", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private val WALLPAPER_MAP = mapOf(
            "wallpaper1" to R.drawable.wallpaper1,
            "wallpaper2" to R.drawable.wallpaper2,
            "wallpaper3" to R.drawable.wallpaper3,
            "wallpaper5" to R.drawable.wallpaper5,
            "wallpaper6" to R.drawable.wallpaper6,
        )
    }

    class HorizontalMarginItemDecoration(
        private val marginPx: Int
    ) : RecyclerView.ItemDecoration() {

        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            with(outRect) {
                // Для всех элементов кроме последнего добавляем отступ справа
                if (parent.getChildAdapterPosition(view) != parent.adapter?.itemCount?.minus(1)) {
                    right = marginPx
                }
            }
        }
    }
}