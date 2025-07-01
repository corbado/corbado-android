package com.corbado.connect.core

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.IOException
import okio.Timeout
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.TimeUnit
import kotlin.math.min

internal class UrlBlockingInterceptor : Interceptor {

    @Volatile private var blockedUrlPaths: MutableSet<String> = CopyOnWriteArraySet()
    @Volatile private var timeoutUrlPaths: MutableMap<String, Long> = ConcurrentHashMap()

    fun setBlockedUrlPaths(paths: Collection<String>) {
        blockedUrlPaths.clear()
        blockedUrlPaths.addAll(paths.map(::normalizePath))
    }

    fun setTimeoutUrlPaths(pathTimeouts: Map<String, Long>) {
        timeoutUrlPaths.clear()
        timeoutUrlPaths.putAll(
            pathTimeouts.mapKeys { (p, _) -> normalizePath(p) }
        )
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request     = chain.request()
        val requestPath = normalizePath(request.url.encodedPath)

        if (blockedUrlPaths.contains(requestPath)) {
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(403)
                .message("Forbidden – blocked by client interceptor")
                .body(
                    "Access to ${request.url.encodedPath} blocked by interceptor"
                        .toResponseBody("text/plain; charset=utf-8".toMediaTypeOrNull())
                )
                .build()
        }

        timeoutUrlPaths[requestPath]?.let { artificialDelayMs ->
            simulateSlowNetwork(chain.call().timeout(), chain.call()::isCanceled, artificialDelayMs)
        }

        return chain.proceed(request)
    }

    private fun normalizePath(path: String): String =
        path.trim().let { if (it.startsWith("/")) it else "/$it" }
            .removeSuffix("/").ifEmpty { "/" }

    /**
     * Sleep in small slices.
     * Abort immediately if
     *   • the call is cancelled  (`callTimeout`, `connectTimeout`, user-cancel), or
     *   • the slice itself is interrupted.
     */
    @Throws(IOException::class)
    private fun simulateSlowNetwork(
        timeout: Timeout,
        isCanceled: () -> Boolean,
        totalDelayMs: Long
    ) {
        val sliceMs       = 100L
        val deadlineNs    = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(totalDelayMs)

        while (true) {
            // 1️⃣  give OkHttp a chance to abort because of connect/read/write timeout
            timeout.throwIfReached()

            // 2️⃣  abort if the call-level timeout fired (OkHttp calls RealCall.cancel())
            if (isCanceled()) {
                throw SocketTimeoutException("Call was cancelled by OkHttp (likely callTimeout)")
            }

            val remainingNs = deadlineNs - System.nanoTime()
            if (remainingNs <= 0) break  // full artificial delay done

            // sleep the next slice
            val sleepMs = min(sliceMs, TimeUnit.NANOSECONDS.toMillis(remainingNs))
            try {
                Thread.sleep(maxOf(1L, sleepMs))
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted during artificial delay", ie)
            }
        }
    }
}