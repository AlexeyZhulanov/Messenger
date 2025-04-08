package com.example.messenger.retrofit.entities.gitlab

import com.example.messenger.model.Repo
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GetRepoResponseEntity(
    val id: Int,
    val name: String,
    @Json(name = "web_url") val webUrl: String,
    @Json(name = "last_activity") var lastActivity: String,
    @Json(name = "hook_push") var hookPush: Boolean = false,
    @Json(name = "hook_merge") var hookMerge: Boolean = false,
    @Json(name = "hook_tag") var hookTag: Boolean = false,
    @Json(name = "hook_issue") var hookIssue: Boolean = false,
    @Json(name = "hook_note") var hookNote: Boolean = false,
    @Json(name = "hook_release") var hookRelease: Boolean = false,
) {
    fun toRepo(): Repo {
        return Repo(
           projectId = id, name = name, url = webUrl, lastActivity = lastActivity, isHookPush = hookPush,
            isHookMerge = hookMerge, isHookTag = hookTag, isHookIssue = hookIssue, isHookNote = hookNote,
            isHookRelease = hookRelease
        )
    }
}