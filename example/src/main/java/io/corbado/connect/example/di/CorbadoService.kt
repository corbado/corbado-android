package io.corbado.connect.example.di

import android.content.Context
import io.corbado.connect.Corbado

object CorbadoService {
    private var instance: Corbado? = null

    fun getInstance(context: Context): Corbado {
        if (instance == null) {
            instance = Corbado(
                projectId = "pro-1045460453059053120",
                frontendApiUrlSuffix = "frontendapi.cloud.corbado-staging.io",
                context = context
            )
        }
        return instance!!
    }
} 