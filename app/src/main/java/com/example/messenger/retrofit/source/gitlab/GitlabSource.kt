package com.example.messenger.retrofit.source.gitlab

import com.example.messenger.model.Repo

interface GitlabSource {
    suspend fun getRepos(token: String): List<Repo>

    suspend fun updateRepo(projectId: Int, hPush: Boolean?, hMerge: Boolean?, hTag: Boolean?, hIssue: Boolean?,
                           hNote: Boolean?, hRelease: Boolean?) : String
}