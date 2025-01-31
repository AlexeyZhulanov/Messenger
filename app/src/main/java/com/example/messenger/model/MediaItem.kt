package com.example.messenger.model

data class MediaItem(
    val type: Int,
    val content: String,
    val user: User? = null
) {
    companion object {
        const val TYPE_MEDIA = 0
        const val TYPE_FILE = 1
        const val TYPE_AUDIO = 2
        const val TYPE_USER = 3
    }
}