package com.corbado.connect.example.di

import android.content.Context
import com.corbado.connect.core.Corbado
import com.corbado.connect.example.MainActivity
import com.corbado.simplecredentialmanager.mocks.VirtualAuthorizationController

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

    fun resetInstance() {
        instance = null
    }
} 