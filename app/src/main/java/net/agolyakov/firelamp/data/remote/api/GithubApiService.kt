package net.agolyakov.firelamp.data.remote.api

import net.agolyakov.firelamp.data.model.github.GithubRelease
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface GithubApiService {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Accept") accept: String = "application/vnd.github.v3+json",
    ): GithubRelease
}
