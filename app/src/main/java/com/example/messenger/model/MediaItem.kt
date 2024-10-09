package com.example.messenger.model

data class MediaItem(
    val type: Int,
    val content: String
) {
    companion object {
        const val TYPE_MEDIA = 0
        const val TYPE_FILE = 1
        const val TYPE_AUDIO = 2
    }
}