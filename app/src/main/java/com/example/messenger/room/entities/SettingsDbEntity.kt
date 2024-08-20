package com.example.messenger.room.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.messenger.model.Settings

@Entity(
    tableName = "settings",
    indices = [
        Index("name", unique = true)
    ]
)
data class SettingsDbEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    var remember: Int,
    var name: String,
    var password: String
) {
    fun toSettings(): Settings = Settings(
        id = id,
        remember = remember,
        name = name,
        password = password
    )

    companion object {
        fun fromUserInput(settings: Settings): SettingsDbEntity = SettingsDbEntity(
            id = settings.id,
            remember = settings.remember,
            name = settings.name!!,
            password = settings.password!!
        )
    }
}