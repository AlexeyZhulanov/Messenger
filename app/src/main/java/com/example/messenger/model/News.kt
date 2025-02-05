package com.example.messenger.model

import com.squareup.moshi.Json

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
)
