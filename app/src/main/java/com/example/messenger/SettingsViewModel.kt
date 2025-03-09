package com.example.messenger

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messenger.di.IoDispatcher
import com.example.messenger.model.FileManager
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.User
import com.luck.picture.lib.config.PictureMimeType
import com.luck.picture.lib.entity.LocalMedia
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
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
    private val fileManager: FileManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
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

    suspend fun getUser(): User {
        return retrofitService.getUser(0) // 0 - current user from jwt
    }

    fun fManagerIsExistAvatar(fileName: String): Boolean {
        return fileManager.isExistAvatar(fileName)
    }

    fun fManagerGetAvatarPath(fileName: String): String {
        return fileManager.getAvatarFilePath(fileName)
    }

    suspend fun fManagerSaveAvatar(fileName: String, fileData: ByteArray) = withContext(ioDispatcher) {
        fileManager.saveAvatarFile(fileName, fileData)
    }

    suspend fun uploadAvatar(avatar: File): String {
        return retrofitService.uploadAvatar(avatar)
    }

    suspend fun downloadAvatar(context: Context, filename: String): String {
        return retrofitService.downloadAvatar(context, filename)
    }

    suspend fun updateAvatar(photo: String) : Boolean {
        return retrofitService.updateProfile(null, photo)
    }
    
    suspend fun updateUserName(username: String) : Boolean {
        return retrofitService.updateProfile(username, null)
    }

    fun updatePassword(oldPassword: String, newPassword: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            // todo здесь нужно сделать нормальный запрос со старым и новым паролем обязательно
            if(newPassword != oldPassword) {
                val result = retrofitService.updatePassword(newPassword)
                if (result) {
                    onSuccess()
                } else {
                    onError("Ошибка: Имя пользователя уже занято")
                }
            } else {
                onError("Ошибка: Старый и новый пароли совпадают")
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