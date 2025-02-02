package com.example.messenger.retrofit.entities.news

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class News(
    val id: Int,
    @Json(name = "written_by") val writtenBy: Int,
    var text: String? = null,
    var images: List<String>? = null,
    var voices: List<String>? = null,
    var files: List<String>? = null,
    var timestamp: Long
) {
    fun toNews(): com.example.messenger.model.News  {
        return com.example.messenger.model.News(
            id = id, writtenBy = writtenBy, text = text, images = images, voices = voices,
            files = files, timestamp = timestamp
        )
    }
}