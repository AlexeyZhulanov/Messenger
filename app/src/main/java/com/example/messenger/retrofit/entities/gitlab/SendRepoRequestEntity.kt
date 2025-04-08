package com.example.messenger.retrofit.entities.gitlab

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SendRepoRequestEntity(
    @Json(name = "hook_push") var hookPush: Boolean? = null,
    @Json(name = "hook_merge") var hookMerge: Boolean? = null,
    @Json(name = "hook_tag") var hookTag: Boolean? = null,
    @Json(name = "hook_issue") var hookIssue: Boolean? = null,
    @Json(name = "hook_note") var hookNote: Boolean? = null,
    @Json(name = "hook_release") var hookRelease: Boolean? = null
)