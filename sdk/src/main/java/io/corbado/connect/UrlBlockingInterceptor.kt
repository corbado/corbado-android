package io.corbado.connect

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.concurrent.CopyOnWriteArraySet

internal class UrlBlockingInterceptor : Interceptor {

    @Volatile
    private var blockedUrlPaths: MutableSet<String> = CopyOnWriteArraySet()

    fun setBlockedUrlPaths(paths: Collection<String>) {
        val normalizedPaths = paths.map { normalizePath(it) }
        blockedUrlPaths.clear()
        blockedUrlPaths.addAll(normalizedPaths)
    }

    private fun normalizePath(path: String): String {
        var p = path.trim()
        if (!p.startsWith("/")) {
            p = "/$p"
        }
        // Optional: remove trailing slash if not root
        if (p.length > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length - 1)
        }
        return p
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestUrl = originalRequest.url
        val requestPath = normalizePath(requestUrl.encodedPath) // Use encodedPath for matching

        if (blockedUrlPaths.contains(requestPath)) {
            return Response.Builder()
                .request(originalRequest)
                .protocol(Protocol.HTTP_1_1) // Or HTTP_2 if you know it
                .code(403) // Forbidden
                .message("Forbidden - URL Blocked by Client Interceptor")
                .body(
                    "Access to this endpoint (${requestUrl.encodedPath}) is blocked by a client-side interceptor."
                        .toResponseBody("text/plain; charset=utf-8".toMediaTypeOrNull())
                )
                .build()
        }

        return chain.proceed(originalRequest)
    }
}