package net.agolyakov.firelamp.data.remote.api

import net.agolyakov.firelamp.data.model.github.GithubRelease
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Streaming

interface GithubApiService {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Accept") accept: String  = "application/vnd.github.v3+json",

    ): GithubRelease

    @GET("repos/{owner}/{repo}/releases")
    suspend fun getAllReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Accept") accept: String  = "application/vnd.github.v3+json",
    ): List<GithubRelease>

    @Streaming
    @GET("repos/{owner}/{repo}/releases/assets/{asset_id}")
    suspend fun downloadAsset(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("asset_id") assetId: String,
        @Header("Accept") accept: String = "application/octet-stream"
    ): ResponseBody
}