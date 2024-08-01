package com.example.messenger

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.messenger.databinding.FragmentMessageBinding
import com.example.messenger.model.Dialog
import com.example.messenger.model.Message
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private var updateJob: Job? = null
    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)
    private val messengerService: MessengerService
        get() = Singletons.messengerRepository as MessengerService
    private val retrofitService: RetrofitService
        get() = Singletons.retrofitRepository as RetrofitService


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        (activity as AppCompatActivity).setSupportActionBar(toolbar)
        (activity as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)
        val backArrow: ImageView = view.findViewById(R.id.back_arrow)
        backArrow.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        val profilePhoto: ImageView = view.findViewById(R.id.photoImageView)
        profilePhoto.setOnClickListener {
            // todo top sheet fragment
        }
        val userName: TextView = view.findViewById(R.id.userNameTextView)
        userName.text = dialog.otherUser.username
        val lastSession: TextView = view.findViewById(R.id.lastSessionTextView)
        lifecycleScope.launch {
            lastSession.text = formatUserSessionDate(retrofitService.getLastSession(dialog.otherUser.id))
        }
        // todo add notifications turn off in room and here
    }

    @SuppressLint("InflateParams")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentMessageBinding.inflate(inflater, container, false)
        adapter = MessageAdapter(object : MessageActionListener {
            override fun onMessageClick(message: Message) {
                TODO("Not yet implemented")
            }

            override fun onMessageLongClick(message: Message) {
                TODO("Not yet implemented")
            }
        }, 123)
        binding.enterMessage.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if(s.isNullOrEmpty()) {
                    binding.micButton.visibility = View.VISIBLE
                    binding.enterButton.visibility = View.INVISIBLE
                } else {
                    binding.enterButton.visibility = View.VISIBLE
                    binding.micButton.visibility = View.INVISIBLE
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.enterButton.setOnClickListener {
            // todo
        }
        binding.micButton.setOnClickListener {
            // todo use lib
        }
        binding.attachButton.setOnClickListener {
            // todo use lib
        }
        binding.emojiButton.setOnClickListener {
            // todo maybe use lib
        }

        val layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerview.layoutManager = layoutManager
        binding.recyclerview.adapter = adapter
        retrofitService.initCompleted.observe(viewLifecycleOwner) { initCompleted ->
            if(initCompleted) {
                updateJob = lifecycleScope.launch {
                    while(isActive) {
                        adapter.messages = retrofitService.getMessages(dialog.id, 0, dialog.countMsg) //todo pagination
                        delay(30000)
                    }
                }
            }
        }

        return binding.root
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

}