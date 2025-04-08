package com.example.messenger.retrofit.api

import com.example.messenger.retrofit.entities.ResponseEntityMessageAnswer
import com.example.messenger.retrofit.entities.gitlab.GetRepoResponseEntity
import com.example.messenger.retrofit.entities.gitlab.SendRepoRequestEntity
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

interface GitlabApi {
    @GET("gitlab/{token}")
    suspend fun getRepos(
        @Path("token") token: String
    ) : List<GetRepoResponseEntity>

    @PUT("gitlab/notifications/{project_id}")
    suspend fun editRepo(
        @Path("project_id") projectId: Int,
        @Body sendRepoRequestEntity: SendRepoRequestEntity
    ) : ResponseEntityMessageAnswer
}