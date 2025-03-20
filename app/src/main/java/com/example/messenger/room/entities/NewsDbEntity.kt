package com.example.messenger.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.messenger.model.News

@Entity(tableName = "news")
data class NewsDbEntity(
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "written_by") val writtenBy: Int,
    @ColumnInfo(name = "header_text") var headerText: String? = null,
    var text: String? = null,
    var images: List<String>? = null,
    var voices: List<String>? = null,
    var files: List<String>? = null,
    @ColumnInfo(name = "is_edited") val isEdited: Boolean = false,
    @ColumnInfo(name = "views_count") val viewsCount: Int = 0,
    var timestamp: Long
) {
    fun toNews(): News = News(
        id = id, writtenBy = writtenBy, headerText = headerText, text = text, images = images,
        voices = voices, files = files, isEdited = isEdited, viewsCount = viewsCount,  timestamp = timestamp
    )
    companion object {
        fun fromUserInput(news: News): NewsDbEntity = NewsDbEntity(
            id = news.id, writtenBy = news.writtenBy, headerText = news.headerText,
            text = news.text, images = news.images, voices = news.voices, files = news.files,
            isEdited = news.isEdited, viewsCount = news.viewsCount, timestamp = news.timestamp
        )
    }
}