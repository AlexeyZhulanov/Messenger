package com.example.messenger

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
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
    private val currentUser: User
) : Fragment() {
    private lateinit var binding: FragmentSettingsBinding
    private val viewModel: SettingsViewModel by viewModels()
    private var fileUpdate: File? = null
    private val alf = ('a'..'z') + ('A'..'Z') + ('0'..'9') + ('А'..'Я') + ('а'..'я') + ('!') + ('$') + (' ')

    @SuppressLint("DiscouragedApi")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val filePickerManager = FilePickerManager(fragment2 = this)
        viewModel.wallpaper.observe(viewLifecycleOwner) { wallpaper ->
            if (wallpaper != "") {
                binding.wallpaperName.text = wallpaper
                val resId =
                    resources.getIdentifier(wallpaper, "drawable", requireContext().packageName)
                if (resId != 0) binding.settingsLayout.background =
                    ContextCompat.getDrawable(requireContext(), resId)
            } else {
                binding.wallpaperName.text = "Classic"
            }
        }
        viewModel.themeNumber.observe(viewLifecycleOwner) { themeNumber ->
            if (themeNumber != 0) binding.colorThemeName.text =
                "Theme ${themeNumber + 1}" else binding.colorThemeName.text = "Classic"
        }
        lifecycleScope.launch {
            binding.usernameTextView.text = currentUser.username
            binding.nameTextView.text = currentUser.name
            val avatar = currentUser.avatar ?: ""
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
        binding.editPhotoButton.setOnClickListener {
            showPopupMenu(it, R.menu.popup_menu_user, filePickerManager, fileUpdate)
        }
        binding.editUsernameButton.setOnClickListener {
            showAddDialog(currentUser.username)
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
        binding.copyNameButton.setOnClickListener {
            val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("label", currentUser.name)
            clipboard.setPrimaryClip(clip)
        }
        binding.changeColorTheme.setOnClickListener {
            showColorThemePopupMenu(it, container as ViewGroup)
        }
        binding.changeWallpaper.setOnClickListener {
            showWallpapersPopupMenu(it, container as ViewGroup)
        }
        return binding.root
    }

    private fun showWallpapersPopupMenu(view: View, container: ViewGroup) {
        val inflater = layoutInflater
        val popupView = inflater.inflate(R.layout.popup_menu_wallpaper_layout, container, false)

        val popupWindow = PopupWindow(
            popupView,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow.showAtLocation(requireView(), Gravity.CENTER, 0, 0)
        val recyclerView = popupView.findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val menuItems = listOf(
            MenuItemData("Classic", R.drawable.lightblue),
            MenuItemData("1.", R.drawable.wallpaper1),
            MenuItemData("2.", R.drawable.wallpaper2),
            MenuItemData("3.", R.drawable.wallpaper3),
            MenuItemData("4.", R.drawable.wallpaper4),
            MenuItemData("5.", R.drawable.wallpaper5),
            MenuItemData("6.", R.drawable.wallpaper6),
            MenuItemData("7.", R.drawable.wallpaper7),
            MenuItemData("8.", R.drawable.wallpaper8),
            MenuItemData("9.", R.drawable.wallpaper9),
            MenuItemData("10.", R.drawable.wallpaper10)
        )
        var temp = ""
        val adapter = PopupMenuWallpaperAdapter(menuItems) { menuItem ->
            temp = when (menuItem.title) {
                "Classic" -> "lightblue"
                "1." -> "wallpaper1"
                "2." -> "wallpaper2"
                "3." -> "wallpaper3"
                "4." -> "wallpaper4"
                "5." -> "wallpaper5"
                "6." -> "wallpaper6"
                "7." -> "wallpaper7"
                "8." -> "wallpaper8"
                "9." -> "wallpaper9"
                "10." -> "wallpaper10"
                else -> ""
            }
            viewModel.updateWallpaper(temp)
            popupWindow.dismiss()
        }

        recyclerView.adapter = adapter

        popupWindow.showAsDropDown(view)
    }

    private fun showColorThemePopupMenu(view: View, container: ViewGroup) {
        val inflater = layoutInflater
        val popupView = inflater.inflate(R.layout.popup_menu_wallpaper_layout, container, false)

        val popupWindow = PopupWindow(
            popupView,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow.showAtLocation(requireView(), Gravity.CENTER, 0, 0)
        val recyclerView = popupView.findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val menuItems = listOf(
            ColorThemeMenuItem(R.color.colorPrimary, R.color.colorAccent, 0),
            ColorThemeMenuItem(R.color.color1_main, R.color.color1_secondary, 1),
            ColorThemeMenuItem(R.color.color2_main, R.color.color2_secondary, 2),
            ColorThemeMenuItem(R.color.color3_main, R.color.color3_secondary, 3),
            ColorThemeMenuItem(R.color.color4_main, R.color.color4_secondary, 4),
            ColorThemeMenuItem(R.color.color5_main, R.color.color5_secondary, 5),
            ColorThemeMenuItem(R.color.color6_main, R.color.color6_secondary, 6),
            ColorThemeMenuItem(R.color.color7_main, R.color.color7_secondary, 7),
            ColorThemeMenuItem(R.color.color8_main, R.color.color8_secondary, 8)
        )
        val adapter = ColorThemeMenuAdapter(menuItems) { menuItem ->
            viewModel.updateTheme(menuItem.themeNumber)
            requireActivity().recreate()
            popupWindow.dismiss()
        }

        recyclerView.adapter = adapter

        popupWindow.showAsDropDown(view)
    }

    @SuppressLint("DiscouragedApi")
    private fun updateWallpapers(wallpaper: String) {
        binding.wallpaperName.text = wallpaper
        if (wallpaper != "") {
            val resId = resources.getIdentifier(wallpaper, "drawable", requireContext().packageName)
            if (resId != 0)
                binding.settingsLayout.background =
                    ContextCompat.getDrawable(requireContext(), resId)
        } else {
            binding.settingsLayout.background = null
            binding.wallpaperName.text = "Classic"
        }
    }

    private fun showPopupMenu(view: View, menuRes: Int, filePickerManager: FilePickerManager, file: File?) {
        val popupMenu = PopupMenu(requireContext(), view)
        popupMenu.menuInflater.inflate(menuRes, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.item_delete -> {
                    if(currentUser.avatar != null) {
                        lifecycleScope.launch {
                            val success = async { viewModel.updateAvatar("delete") }
                            if(success.await()) {
                                requireActivity().recreate()
                            }
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
                            val path = async { viewModel.uploadAvatar(File(photo[0].availablePath)) }
                            val success = async { viewModel.updateAvatar(path.await()) }
                            if(success.await()) {
                                requireActivity().recreate()
                            }
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
                            val path = async { viewModel.uploadAvatar(File(photo[0].availablePath)) }
                            val success = async { viewModel.updateAvatar(path.await()) }
                            if(success.await()) {
                                requireActivity().recreate()
                            }
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
                    val success = async { viewModel.updateUserName(input) }
                    if(success.await()) requireActivity().recreate()
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
}