package com.example.messenger.model

import android.os.Parcelable
import com.squareup.moshi.Json
import kotlinx.parcelize.Parcelize
import java.io.File

@Parcelize
data class News(
    val id: Int,
    @Json(name = "written_by") val writtenBy: Int,
    @Json(name = "header_text") var headerText: String? = null,
    var text: String? = null,
    var images: List<String>? = null,
    var voices: List<String>? = null,
    var files: List<String>? = null,
    @Json(name = "is_edited") var isEdited: Boolean = false,
    @Json(name = "views_count") val viewsCount: Int = 0,
    var timestamp: Long
) : Parcelable

@Parcelize
data class ParcelableFile(val path: String) : Parcelable {
    fun toFile() = File(path)
}