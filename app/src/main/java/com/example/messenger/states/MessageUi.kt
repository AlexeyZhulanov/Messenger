package com.example.messenger.states

import com.example.messenger.model.Message

data class MessageUi(
    val message: Message,
    val formattedDate: String,
    val formattedTime: String,
    val parsedText: CharSequence? = null,
    val isFirstPage: Boolean = false,

    // Checkbox на удаление и пересылку
    val isSelected: Boolean = false,
    val isShowCheckbox: Boolean = false,

    // Reply
    val replyState: ReplyState? = null,
    val isHighlighted: Boolean = false,

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
        val localPath: String,
        val mimeType: String,
        val duration: Long
    ) : ImageState()
    object Error : ImageState()
}

sealed class ImagesState {
    object Loading : ImagesState()
    data class Ready(
        val localPaths: List<String>,
        val mimeTypes: List<String>,
        val durations: List<Long>
    ) : ImagesState()
    object Error : ImagesState()
}

sealed class AvatarState {
    object Loading : AvatarState()
    data class Ready(val localPath: String) : AvatarState()
    object Error : AvatarState()
}

sealed class ReplyState {
    object Loading : ReplyState()

    data class Ready(
        val referenceMessageId: Int,
        val previewText: String,
        val previewImagePath: String?, // локальный путь
        val username: String?
    ) : ReplyState()

    object Error : ReplyState()
}

data class ReplyPreview(
    val text: String,
    val imageName: String?,
    val username: String?
)