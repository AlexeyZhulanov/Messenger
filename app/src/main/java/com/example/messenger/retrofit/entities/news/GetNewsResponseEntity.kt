package com.example.messenger.retrofit.entities.news

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class News(
    val id: Int,
    @Json(name = "written_by") val writtenBy: Int,
    @Json(name = "header_text") var headerText: String,
    var text: String? = null,
    var images: List<String>? = null,
    var voices: List<String>? = null,
    var files: List<String>? = null,
    @Json(name = "is_edited") var isEdited: Boolean = false,
    @Json(name = "views_count") val viewsCount: Int = 0,
    var timestamp: Long
) {
    fun toNews(): com.example.messenger.model.News  {
        return com.example.messenger.model.News(
            id = id, writtenBy = writtenBy, headerText = headerText, text = text, images = images,
            voices = voices, files = files, isEdited = isEdited, viewsCount = viewsCount,  timestamp = timestamp
        )
    }
}