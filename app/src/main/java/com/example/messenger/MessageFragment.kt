package com.example.messenger

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Bundle
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
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.messenger.databinding.FragmentMessageBinding
import com.example.messenger.model.Dialog
import com.example.messenger.model.Message
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import com.example.messenger.picker.FilePickerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MessageFragment(
    private val dialog: Dialog
) : Fragment() {
    private lateinit var binding: FragmentMessageBinding
    private lateinit var adapter: MessageAdapter
    private lateinit var preferences: SharedPreferences
    private var lastSessionString: String = ""
    private var countMsg = dialog.countMsg
    private var editFlag = false
    private var updateJob: Job? = null
    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)
    private val messengerService: MessengerService
        get() = Singletons.messengerRepository as MessengerService
    private val retrofitService: RetrofitService
        get() = Singletons.retrofitRepository as RetrofitService


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
                    showPopupMenuMessage(itemView, R.menu.popup_menu_message, message)
                }

                override fun onMessageLongClick(message: Message, itemView: View) {
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
                        val messagesToDelete = adapter.getDeleteList()
                        if (messagesToDelete.isNotEmpty()) {
                            uiScope.launch {
                                binding.progressBar.visibility = View.VISIBLE
                                val response = async { retrofitService.deleteMessages(messagesToDelete) }
                                binding.floatingActionButtonDelete.visibility = View.GONE
                                countMsg -= messagesToDelete.size
                                adapter.clearPositions()
                                if(response.await()) {
                                    startMessagePolling()
                                    binding.progressBar.visibility = View.GONE
                                }
                            }
                        }
                    }
                }
                }
            }, dialog.otherUser.id)
        binding.enterMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.isNullOrEmpty()) {
                    binding.micButton.visibility = View.VISIBLE
                    if(!editFlag) {
                        binding.enterButton.visibility = View.INVISIBLE
                    } else binding.editButton.visibility = View.GONE
                } else {
                    binding.micButton.visibility = View.INVISIBLE
                    if(!editFlag) {
                        binding.enterButton.visibility = View.VISIBLE
                    } else binding.editButton.visibility = View.VISIBLE
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.micButton.setOnClickListener {
            // todo use lib
        }
        binding.attachButton.setOnClickListener {
            filePickerManager.openFilePicker(isCircle = false, isFreeStyleCrop = false)
        }
        binding.attachButton.setOnLongClickListener {
            ChoosePickFragment(object: ChoosePickListener {
                override fun onGalleryClick() {
                    Log.d("testWork", "OK")
                    filePickerManager.openFilePicker(isCircle = false, isFreeStyleCrop = true)
                }
                override fun onFileClick() {
                    TODO("Not yet implemented")
                }
            }).show(childFragmentManager, "ChoosePickTag")
            true
        }
        binding.emojiButton.setOnClickListener {
            // todo maybe use lib
        }

        val layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerview.layoutManager = layoutManager
        binding.recyclerview.adapter = adapter
        binding.recyclerview.addItemDecoration(VerticalSpaceItemDecoration(15))
        binding.enterButton.setOnClickListener {
            val text = binding.enterMessage.text.toString()
            uiScope.launch {
                retrofitService.sendMessage(dialog.id, text, null, null, null)
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

    private fun showPopupMenuMessage(view: View, menuRes: Int, message: Message) {
        val popupMenu = PopupMenu(requireContext(), view)
        popupMenu.menuInflater.inflate(menuRes, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.item_edit -> {
                    editFlag = true
                    val editText: EditText = requireView().findViewById(R.id.enter_message)
                    editText.setText(message.text)
                    editText.setSelection(message.text?.length ?: 0)
                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    editText.postDelayed({
                        editText.requestFocus()
                        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
                    }, 100)
                    val editButton: ImageView = requireView().findViewById(R.id.edit_button)
                    editButton.setOnClickListener {
                        uiScope.launch {
                            stopMessagePolling()
                            val response = async { retrofitService.editMessage(message.id, editText.text.toString(), null, null, null) }
                            if(response.await()) {
                                editFlag = false
                                editText.setText("")
                                startMessagePolling()
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
}
