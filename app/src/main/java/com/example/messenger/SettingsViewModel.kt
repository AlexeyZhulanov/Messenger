package com.example.messenger

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messenger.di.IoDispatcher
import com.example.messenger.model.FileManager
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.User
import com.example.messenger.model.WebSocketService
import com.example.messenger.model.appsettings.AppSettings
import com.google.firebase.messaging.FirebaseMessaging
import com.luck.picture.lib.config.PictureMimeType
import com.luck.picture.lib.entity.LocalMedia
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: SharedPreferences,
    private val retrofitService: RetrofitService,
    private val fileManager: FileManager,
    private val appSettings: AppSettings,
    private val messengerService: MessengerService,
    private val webSocketService: WebSocketService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _wallpaper = MutableLiveData<String>()
    val wallpaper: LiveData<String> get() = _wallpaper

    private val _themeNumber = MutableLiveData<Int>()
    val themeNumber: LiveData<Int> get() = _themeNumber

    private val _currentUser = MutableLiveData<User>()
    val currentUser: LiveData<User> get() = _currentUser

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

    fun setUser(user: User) {
        _currentUser.value = user
    }

    fun updateUsernameValue(newUsername: String) {
        _currentUser.value = _currentUser.value?.copy(username = newUsername)
    }

    fun updateAvatarValue(newAvatar: String) {
        _currentUser.value = _currentUser.value?.copy(avatar = newAvatar)
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
        return try {
            retrofitService.uploadAvatar(avatar)
        } catch (e: Exception) { "" }
    }

    suspend fun downloadAvatar(context: Context, filename: String): String {
        return retrofitService.downloadAvatar(context, filename)
    }

    suspend fun updateAvatar(photo: String) : Boolean {
        return try {
            retrofitService.updateProfile(null, photo)
        } catch (e: Exception) { false }
    }
    
    suspend fun updateUserName(username: String) : Boolean {
        return try {
            retrofitService.updateProfile(username, null)
        } catch (e: Exception) { false }
    }

    fun updatePassword(oldPassword: String, newPassword: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            if(newPassword != oldPassword) {
                val result = try {
                    retrofitService.updatePassword(oldPassword, newPassword)
                } catch (e: Exception) { false }
                if (result) {
                    onSuccess()
                } else {
                    onError("Ошибка: Неверный старый пароль либо нет сети")
                }
            } else {
                onError("Ошибка: Старый и новый пароли совпадают")
            }
        }
    }

    suspend fun deleteFCMToken() : Boolean {
        return try {
            retrofitService.deleteFCMToken()
        } catch (e: Exception) { false }
    }

    fun clearAllAppFiles(callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val bool = fileManager.clearAllAppFiles()
            callback(bool)
        }
    }

    fun clearCurrentUser() {
        viewModelScope.launch {
            withContext(NonCancellable) {
                Log.d("testClearUser", "OK")
                webSocketService.disconnect()
                FirebaseMessaging.getInstance().deleteToken().await()
                messengerService.deleteCurrentUser()
                appSettings.setCurrentAccessToken(null)
                appSettings.setCurrentRefreshToken(null)
                appSettings.setRemember(false)
                appSettings.setCurrentGitlabToken(null)
                appSettings.setFCMToken(null)
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