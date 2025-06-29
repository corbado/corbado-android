package com.corbado.connect

import okhttp3.Interceptor
import okhttp3.Response

internal class ProcessIdInterceptor : Interceptor {
    @Volatile
    var processId: String? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val processId = this.processId

        return if (processId != null) {
            val builder = originalRequest.newBuilder()
                .header("x-corbado-process-id", processId)
            chain.proceed(builder.build())
        } else {
            chain.proceed(originalRequest)
        }
    }
} 