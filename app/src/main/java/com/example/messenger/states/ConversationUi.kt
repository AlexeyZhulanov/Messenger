package com.example.messenger.states

import com.example.messenger.model.Conversation

data class ConversationUi(
    val conversation: Conversation,
    val dateText: String,
    val avatarState: AvatarState? = null
)