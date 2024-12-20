package com.example.messenger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.messenger.databinding.FragmentVacationBinding
import java.text.SimpleDateFormat
import java.util.Locale

class VacationFragment(
    private val start: String,
    private val end: String) : Fragment() {

    private lateinit var binding: FragmentVacationBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentVacationBinding.inflate(inflater, container, false)
        binding.startDateTextView.text = formatVacationDate(start)
        binding.endDateTextView.text = formatVacationDate(end)
        return binding.root
    }

    private fun formatVacationDate(dateString: String): String? {
        return try {
            // Укажите исходный формат даты
            val inputFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
            // Укажите желаемый формат
            val outputFormat = SimpleDateFormat("HH:mm dd.MM.yyyy", Locale.getDefault())
            // Парсим строку в Date
            val date = inputFormat.parse(dateString)
            // Форматируем дату в нужный формат
            date?.let { outputFormat.format(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            null // Возвращаем null в случае ошибки
        }
    }
}