package com.example.messenger.model

data class Repo(
    val projectId: Int,
    val name: String,
    val url: String,
    var lastActivity: String,
    var isHookPush: Boolean,
    var isHookMerge: Boolean,
    var isHookTag: Boolean,
    var isHookIssue: Boolean,
    var isHookNote: Boolean,
    var isHookRelease: Boolean,
)