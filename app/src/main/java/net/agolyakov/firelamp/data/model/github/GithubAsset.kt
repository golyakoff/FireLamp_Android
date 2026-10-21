package net.agolyakov.firelamp.data.model.github

import com.google.gson.annotations.SerializedName

data class GithubAsset(
    @SerializedName("id")
    val id: Long,

    @SerializedName("name")
    val name: String,

    @SerializedName("browser_download_url")
    val browserDownloadUrl: String,

    @SerializedName("url")
    val apiUrl: String,

    @SerializedName("size")
    val size: Long,

    @SerializedName("digest")
    val digest: String? = null, // "sha256:abcdef..."

    @SerializedName("content_type")
    val contentType: String,

    @SerializedName("created_at")
    val createdAt: String,

    @SerializedName("updated_at")
    val updatedAt: String
) {
    // Calculated clean SHA256 hash without prefix
    val sha256Hash: String?
        get() = digest?.removePrefix("sha256:")
}