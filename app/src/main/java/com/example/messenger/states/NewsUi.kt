package com.example.messenger.states

import android.os.Parcelable
import com.example.messenger.model.News
import kotlinx.parcelize.Parcelize

@Parcelize
data class NewsUi(
    val news: News,
    val formattedDate: String,

    val imagesState: ImagesState? = null,
    val filesState: FilesState? = null,
    val voicesState: VoicesState? = null
) : Parcelable

@Parcelize
data class VoiceItem(
    val localPath: String,
    val duration: Long,
    val sample: List<Int>
) : Parcelable

@Parcelize
sealed class VoicesState : Parcelable {
    object Loading : VoicesState()
    data class Ready(val items: List<VoiceItem>) : VoicesState()
    object Error : VoicesState()
}

@Parcelize
data class FileItem(
    val localPath: String,
    val fileName: String,
    val fileSize: String
) : Parcelable

@Parcelize
sealed class FilesState : Parcelable {
    object Loading : FilesState()
    data class Ready(val items: List<FileItem>) : FilesState()
    object Error : FilesState()
}

data class NewsAttachmentsState(
    val imagesState: ImagesState? = null,
    val voicesState: VoicesState? = null,
    val filesState: FilesState? = null
)

data class AudioPlaybackState(
    val playingPath: String? = null,
    val isPlaying: Boolean = false,
    val progress: Int = 0
)