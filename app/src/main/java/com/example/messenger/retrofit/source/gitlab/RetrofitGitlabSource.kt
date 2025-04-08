package com.example.messenger.retrofit.source.gitlab

import com.example.messenger.model.Repo
import com.example.messenger.retrofit.api.GitlabApi
import com.example.messenger.retrofit.entities.gitlab.SendRepoRequestEntity
import com.example.messenger.retrofit.source.base.BaseRetrofitSource
import com.example.messenger.retrofit.source.base.RetrofitConfig

class RetrofitGitlabSource(
    config: RetrofitConfig
) : BaseRetrofitSource(config), GitlabSource {

    private val gitlabApi = retrofit.create(GitlabApi::class.java)

    override suspend fun getRepos(token: String): List<Repo> = wrapRetrofitExceptions {
        gitlabApi.getRepos(token).map { it.toRepo() }
    }

    override suspend fun updateRepo(projectId: Int, hPush: Boolean?, hMerge: Boolean?, hTag: Boolean?,
        hIssue: Boolean?, hNote: Boolean?, hRelease: Boolean?): String = wrapRetrofitExceptions {
        val sendRepoRequestEntity = SendRepoRequestEntity(hPush, hMerge, hTag, hIssue, hNote, hRelease)
        gitlabApi.editRepo(projectId, sendRepoRequestEntity).message
    }
}