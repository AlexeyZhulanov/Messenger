package com.example.messenger

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.DialogFragment

interface OnSaveButtonClickListener {
    fun onSaveButtonClicked()
}

class SaveKeyDialogFragment : DialogFragment() {

    private var saveButtonClickListener: OnSaveButtonClickListener? = null

    fun setSaveButtonClickListener(listener: OnSaveButtonClickListener) {
        this.saveButtonClickListener = listener
    }

    private var countdown = 5 // Начальное значение отсчета
    private lateinit var saveButton: Button
    private lateinit var countdownHandler: Handler
    private lateinit var countdownRunnable: Runnable

    override fun onStart() {
        super.onStart()
        // Настройка размеров диалога
        val width = (resources.displayMetrics.widthPixels * 0.94).toInt()
        val height = ViewGroup.LayoutParams.WRAP_CONTENT
        dialog?.window?.setLayout(width, height)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialog_save_key, container, false)

        saveButton = view.findViewById(R.id.saveButton)
        saveButton.setOnClickListener {
            saveButtonClickListener?.onSaveButtonClicked()
            dismiss()
        }
        // Запуск отсчета
        startCountdown()
        return view
    }

    private fun startCountdown() {
        countdownHandler = Handler(Looper.getMainLooper())
        countdownRunnable = object : Runnable {
            @SuppressLint("SetTextI18n")
            override fun run() {
                if (countdown > 0) {
                    saveButton.text = "Сохранить ($countdown)"
                    countdown--
                    countdownHandler.postDelayed(this, 1000)
                } else {
                    saveButton.text = "Сохранить"
                    saveButton.isEnabled = true
                }
            }
        }
        countdownHandler.post(countdownRunnable) // Запускаем отсчет
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Останавливаем отсчет при уничтожении диалога
        countdownHandler.removeCallbacks(countdownRunnable)
    }
}