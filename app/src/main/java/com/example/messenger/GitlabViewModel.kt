package com.example.messenger

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messenger.model.MessengerService
import com.example.messenger.model.Repo
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.appsettings.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class GitlabViewModel @Inject constructor(
    private val messengerService: MessengerService,
    private val retrofitService: RetrofitService,
    private val appSettings: AppSettings
) : ViewModel() {

    private val _repos = MutableLiveData<List<Repo>>()
    val repos: LiveData<List<Repo>> = _repos

    var isNeedFetchRepos = false

    init {
        fetchRepos()
    }

    fun fetchRepos() {
        viewModelScope.launch {
            try {
                if(!isNeedFetchRepos) {
                    val initialRepos = messengerService.getRepos()
                    _repos.postValue(initialRepos)
                } else isNeedFetchRepos = false

                val token = appSettings.getCurrentGitlabToken()

                if(!token.isNullOrEmpty()) {
                    val updatedRepos = retrofitService.getRepos(token)
                    updatedRepos.forEach {
                        it.lastActivity = formatStringTime(it.lastActivity)
                    }
                    _repos.postValue(updatedRepos)
                    messengerService.replaceRepos(updatedRepos)
                }

            } catch (e: Exception) { return@launch }
        }
    }

    suspend fun updateRepo(projectId: Int, hooks: List<Boolean?>, callback: (Boolean) -> Unit) {
        try {
            val success = retrofitService.updateRepo(projectId, hooks[0], hooks[1], hooks[2], hooks[3], hooks[4], hooks[5])
            callback(success)
        } catch (e: Exception) {
            callback(false)
        }
    }

    fun getGitlabToken(): String {
        return appSettings.getCurrentGitlabToken() ?: "Токен отсутствует"
    }

    fun changeGitlabToken(token: String) {
        if(token != "") appSettings.setCurrentGitlabToken(token)
    }

    private fun formatStringTime(time: String): String {
        val timestamp = parseTimeToLong(time)
        return formatRepoDate(timestamp)
    }

    private fun parseTimeToLong(timeString: String): Long {
        val formatter = DateTimeFormatter.ISO_INSTANT
        return Instant.from(formatter.parse(timeString)).toEpochMilli()
    }

    private fun formatRepoDate(timestamp: Long?): String {
        if (timestamp == null) return "не было изменений"

        val greenwichSessionDate = Calendar.getInstance().apply {
            timeInMillis = timestamp
        }
        val now = Calendar.getInstance()

        val diffInMillis = now.timeInMillis - greenwichSessionDate.timeInMillis
        val diffInMinutes = (diffInMillis / 60000).toInt()

        val dateFormatTime = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormatDayMonth = SimpleDateFormat("d MMMM", Locale.getDefault())
        val dateFormatYear = SimpleDateFormat("d.MM.yyyy", Locale.getDefault())

        return when {
            diffInMinutes < 5 -> "только что"
            diffInMinutes in 5..20 -> "$diffInMinutes минут назад"
            diffInMinutes % 10 == 1 && diffInMinutes in 21..59 -> "$diffInMinutes минуту назад"
            diffInMinutes % 10 in 2..4 && diffInMinutes in 21..59 -> "$diffInMinutes минуты назад"
            diffInMinutes < 60 -> "$diffInMinutes минут назад"
            diffInMinutes < 1440 -> dateFormatTime.format(greenwichSessionDate.time)
            else -> {
                // Проверка года
                val currentYear = now.get(Calendar.YEAR)
                val sessionYear = greenwichSessionDate.get(Calendar.YEAR)
                if (currentYear == sessionYear) {
                    dateFormatDayMonth.format(greenwichSessionDate.time)
                } else {
                    dateFormatYear.format(greenwichSessionDate.time)
                }
            }
        }
    }
}