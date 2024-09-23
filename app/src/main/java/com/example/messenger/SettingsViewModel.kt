package com.example.messenger

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messenger.model.FileManager
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.Settings
import com.example.messenger.model.User
import com.example.messenger.room.entities.SettingsDbEntity
import com.luck.picture.lib.config.PictureMimeType
import com.luck.picture.lib.entity.LocalMedia
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: SharedPreferences,
    private val retrofitService: RetrofitService,
    private val messengerService: MessengerService,
    private val fileManager: FileManager
) : ViewModel() {

    private val _wallpaper = MutableLiveData<String>()
    val wallpaper: LiveData<String> get() = _wallpaper

    private val _themeNumber = MutableLiveData<Int>()
    val themeNumber: LiveData<Int> get() = _themeNumber

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _wallpaper.value = prefs.getString(PREF_WALLPAPER, "")
            _themeNumber.value = prefs.getInt(PREF_THEME, 0)
        }
    }

    fun updateWallpaper(wallpaper: String) {
        prefs.edit().putString(PREF_WALLPAPER, wallpaper).apply()
        _wallpaper.value = wallpaper
    }

    fun updateTheme(themeNumber: Int) {
        prefs.edit().putInt(PREF_THEME, themeNumber).apply()
        _themeNumber.value = themeNumber
    }

    suspend fun getUser(): User = withContext(Dispatchers.IO) {
        return@withContext retrofitService.getUser(0) // 0 - current user from jwt
    }

    suspend fun fManagerIsExist(fileName: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext fileManager.isExist(fileName)
    }

    suspend fun fManagerGetFilePath(fileName: String): String = withContext(Dispatchers.IO) {
        return@withContext fileManager.getFilePath(fileName)
    }

    suspend fun fManagerSaveFile(fileName: String, fileData: ByteArray) = withContext(Dispatchers.IO) {
        fileManager.saveFile(fileName, fileData)
    }

    suspend fun uploadPhoto(photo: File): String = withContext(Dispatchers.IO) {
        return@withContext retrofitService.uploadPhoto(photo)
    }

    suspend fun downloadFile(context: Context, folder: String, filename: String): String = withContext(Dispatchers.IO) {
        return@withContext retrofitService.downloadFile(context, folder, filename)
    }

    suspend fun deleteFile(folder: String, filename: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext retrofitService.deleteFile(folder, filename)
    }

    suspend fun updateAvatar(photo: String) : Boolean = withContext(Dispatchers.IO) {
        return@withContext retrofitService.updateProfile(null, photo)
    }
    
    suspend fun updateUserName(username: String) : Boolean = withContext(Dispatchers.IO) {
        return@withContext retrofitService.updateProfile(username, null)
    }

    fun updatePassword(oldPassword: String, newPassword: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val settings = messengerService.getSettings()
            val savedPassword = settings.password
            if(savedPassword == oldPassword) {
                val result = async(Dispatchers.IO) { retrofitService.updatePassword(newPassword) }
                if (result.await()) {
                    settings.password = newPassword
                    messengerService.updateSettings(settings)
                    onSuccess()
                } else {
                    onError("Ошибка: Имя пользователя уже занято")
                }
            } else {
                onError("Ошибка: Неверный старый пароль")
            }
        }
    }

    fun fileToLocalMedia(file: File?) : LocalMedia {
        val localMedia = LocalMedia()

        // Установите путь файла
        localMedia.path = file?.absolutePath

        // Определите MIME тип файла на основе его расширения
        localMedia.mimeType = when (file?.extension?.lowercase(Locale.ROOT)) {
            "jpg", "jpeg" -> PictureMimeType.ofJPEG()
            "png" -> PictureMimeType.ofPNG()
            "mp4" -> PictureMimeType.ofMP4()
            "avi" -> PictureMimeType.ofAVI()
            "gif" -> PictureMimeType.ofGIF()
            else -> PictureMimeType.MIME_TYPE_AUDIO // Или другой тип по умолчанию
        }

        // Установите дополнительные свойства
        localMedia.isCompressed = false // Или true, если вы хотите сжать изображение
        localMedia.isCut = false // Если это изображение было обрезано
        localMedia.isOriginal = false // Если это оригинальный файл
        localMedia.duration = 0 // Для изображений длительность обычно равна 0
        return localMedia
    }
}