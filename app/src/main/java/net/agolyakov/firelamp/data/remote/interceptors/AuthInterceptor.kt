package net.agolyakov.firelamp.data.remote.interceptors

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val token: String
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
            .header("User-Agent", "FireLamp-Android-App") // GitHub requires it

        // Token is optional: public repos work unauthenticated (lower rate limits)
        if (token.isNotBlank()) {
            builder.header("Authorization", "token $token")
        }

        return chain.proceed(builder.build())
    }
}
