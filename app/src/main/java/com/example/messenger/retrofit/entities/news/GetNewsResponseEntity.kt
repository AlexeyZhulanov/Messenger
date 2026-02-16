package com.example.messenger.retrofit.entities.news

import com.example.messenger.model.News
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GetNewsResponseEntity(
    val id: Int,
    @param:Json(name = "written_by") val writtenBy: Int,
    @param:Json(name = "header_text") var headerText: String,
    var text: String? = null,
    var images: List<String>? = null,
    var voices: List<String>? = null,
    var files: List<String>? = null,
    @param:Json(name = "is_edited") var isEdited: Boolean = false,
    @param:Json(name = "views_count") val viewsCount: Int = 0,
    var timestamp: Long
) {
    fun toNews(): News {
        return News(
            id = id, writtenBy = writtenBy, headerText = headerText, text = text, images = images,
            voices = voices, files = files, isEdited = isEdited, viewsCount = viewsCount, timestamp = timestamp
        )
    }
}