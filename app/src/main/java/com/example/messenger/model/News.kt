package com.example.messenger.model

import com.squareup.moshi.Json

data class News(
    val id: Int,
    @Json(name = "written_by") val writtenBy: Int,
    var text: String? = null,
    var images: List<String>? = null,
    var voices: List<String>? = null,
    var files: List<String>? = null,
    var timestamp: Long
    )