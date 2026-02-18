package com.example.messenger.states

import android.net.Uri
import com.example.messenger.model.Message

data class MessageUi(
    val message: Message,
    val formattedDate: String,
    val formattedTime: String,
    val parsedText: CharSequence? = null,

    // Файлы
    val voiceState: VoiceState? = null,
    val fileState: FileState? = null,
    val imageState: ImageState? = null,
    val imagesState: ImagesState? = null,

    // Аватарки
    val avatarState: AvatarState? = null,
    val username: String? = null,
    val showUsername: Boolean = false,
    val showAvatar: Boolean = false,
)

sealed class VoiceState {
    object Loading : VoiceState()
    data class Ready(
        val localPath: String,
        val duration: Long
    ) : VoiceState()
    object Error : VoiceState()
}

sealed class FileState {
    object Loading : FileState()
    data class Ready(
        val localPath: String,
        val fileName: String,
        val fileSize: String
    ) : FileState()
    object Error : FileState()
}

sealed class ImageState {
    object Loading : ImageState()
    data class Ready(
        val localPath: String
    ) : ImageState()
    object Error : ImageState()
}

sealed class ImagesState {
    object Loading : ImagesState()
    data class Ready(
        val localPaths: List<String>
    ) : ImagesState()
    object Error : ImagesState()
}

sealed class AvatarState {
    object Loading : AvatarState()
    data class Ready(val uri: Uri) : AvatarState()
    object Error : AvatarState()
}

data class GroupDisplayInfo(
    val username: String?,
    val avatar: String?,
    val showUsername: Boolean,
    val showAvatar: Boolean
)