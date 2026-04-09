package com.openclaw.agent.core.web.cookie

import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp Interceptor that injects stored cookies for matching domains.
 */
class CookieAuthInterceptor(
    private val cookieVault: CookieVault,
    private val site: String
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val cookies = cookieVault.getCookies(site)

        return if (!cookies.isNullOrBlank()) {
            val newRequest = request.newBuilder()
                .header("Cookie", cookies)
                .build()
            chain.proceed(newRequest)
        } else {
            chain.proceed(request)
        }
    }
}
