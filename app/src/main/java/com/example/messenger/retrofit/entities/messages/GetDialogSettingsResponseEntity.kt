package com.example.messenger.retrofit.entities.messages

import com.example.messenger.model.ConversationSettings
import com.squareup.moshi.Json

data class GetDialogSettingsResponseEntity(
    @Json(name = "can_delete") val canDelete: Boolean,
    @Json(name = "auto_delete_interval") val autoDeleteInterval: Int
) {
    fun toConversationSettings() : ConversationSettings = ConversationSettings(
        canDelete = canDelete, autoDeleteInterval = autoDeleteInterval)
}