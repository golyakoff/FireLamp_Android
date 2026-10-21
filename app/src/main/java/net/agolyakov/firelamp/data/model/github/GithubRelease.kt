package net.agolyakov.firelamp.data.model.github

import com.google.gson.annotations.SerializedName

data class GithubRelease(
    @SerializedName("tag_name")
    val tagName: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("published_at")
    val publishedAt: String,

    @SerializedName("html_url")
    val htmlUrl: String = "",

    @SerializedName("prerelease")
    val prerelease: Boolean = false,

    @SerializedName("assets")
    val assets: List<GithubAsset> = emptyList(),

    @SerializedName("body")
    val body: String? = null
)