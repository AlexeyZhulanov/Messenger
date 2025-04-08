package com.example.messenger.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.messenger.model.Repo

@Entity(tableName = "gitlab")
data class GitlabDbEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "project_id") val projectId: Int,
    val name: String,
    @ColumnInfo(name = "web_url") val webUrl: String,
    @ColumnInfo(name = "last_activity") var lastActivity: String,
    @ColumnInfo(name = "hook_push") var hookPush: Boolean = false,
    @ColumnInfo(name = "hook_merge") var hookMerge: Boolean = false,
    @ColumnInfo(name = "hook_tag") var hookTag: Boolean = false,
    @ColumnInfo(name = "hook_issue") var hookIssue: Boolean = false,
    @ColumnInfo(name = "hook_note") var hookNote: Boolean = false,
    @ColumnInfo(name = "hook_release") var hookRelease: Boolean = false,
) {
    fun toRepo(): Repo = Repo(
        projectId = projectId, name = name, url = webUrl, lastActivity = lastActivity, isHookPush = hookPush,
        isHookMerge = hookMerge, isHookTag = hookTag, isHookIssue = hookIssue, isHookNote = hookNote,
        isHookRelease = hookRelease
    )

    companion object {
        fun fromUserInput(repo: Repo): GitlabDbEntity = GitlabDbEntity(
            projectId = repo.projectId, name = repo.name, webUrl = repo.url, lastActivity = repo.lastActivity,
            hookPush = repo.isHookPush, hookMerge = repo.isHookMerge, hookTag = repo.isHookTag,
            hookIssue = repo.isHookIssue, hookNote = repo.isHookNote, hookRelease = repo.isHookRelease
        )
    }
}