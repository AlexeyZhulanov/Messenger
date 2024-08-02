package com.example.messenger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
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
import com.example.messenger.databinding.FragmentDialogInfoBinding
import com.example.messenger.model.Dialog
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

class DialogInfoFragment(
    private val dialog: Dialog,
    private val lastSessionString : String
) : Fragment() {
    private lateinit var binding: FragmentDialogInfoBinding
    private lateinit var preferences: SharedPreferences
    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)
    private val messengerService: MessengerService
        get() = Singletons.messengerRepository as MessengerService
    private val retrofitService: RetrofitService
        get() = Singletons.retrofitRepository as RetrofitService

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentDialogInfoBinding.inflate(inflater, container, false)
        preferences = requireContext().getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE)
        val wallpaper = preferences.getString(PREF_WALLPAPER, "")
        if(wallpaper != "") {
            val resId = resources.getIdentifier(wallpaper, "drawable", requireContext().packageName)
            if(resId != 0)
                binding.dialogInfoLayout.background = ContextCompat.getDrawable(requireContext(), resId)
        }
        binding.userNameTextView.text = dialog.otherUser.username
        binding.lastSessionTextView.text = lastSessionString
        binding.nickTextView.text = dialog.otherUser.name
        // todo switch notification room
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