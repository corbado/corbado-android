package io.corbado.connect.example.di

import android.content.Context
import io.corbado.connect.Corbado
import io.corbado.connect.example.MainActivity
import io.corbado.simplecredentialmanager.mocks.VirtualAuthorizationController

object CorbadoService {
    private var instance: Corbado? = null

    fun getInstance(context: Context): Corbado {
        if (instance == null) {
            instance = if (MainActivity.isUITestMode && MainActivity.virtualAuthorizationController != null) {
                // In test mode, create Corbado with virtual authenticator
                Corbado(
                    projectId = "pro-1045460453059053120",
                    frontendApiUrlSuffix = "frontendapi.cloud.corbado-staging.io",
                    context = context,
                    authorizationController = MainActivity.virtualAuthorizationController as VirtualAuthorizationController
                )
            } else {
                // Normal mode
                Corbado(
                    projectId = "pro-1045460453059053120",
                    frontendApiUrlSuffix = "frontendapi.cloud.corbado-staging.io",
                    context = context
                )
            }
        }
        return instance!!
    }
} 