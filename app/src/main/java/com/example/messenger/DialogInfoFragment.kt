package com.example.messenger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.messenger.databinding.FragmentDialogInfoBinding
import com.example.messenger.model.Dialog
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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
        //binding.switchNotifications.isChecked = dialog // todo
        binding.copyImageView.setOnClickListener {
            val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("label", dialog.otherUser.name)
            clipboard.setPrimaryClip(clip)
        }
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
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
                // todo
                true
            }
            R.id.delete_all_messages -> {
               // todo
                true
            }
            R.id.delete_dialog -> {
                // todo
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

}