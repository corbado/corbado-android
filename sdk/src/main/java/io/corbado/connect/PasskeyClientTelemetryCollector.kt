package io.corbado.connect

import android.os.Build
import com.corbado.api.models.NativeMeta

internal object PasskeyClientTelemetryCollector {
    fun collectData(): NativeMeta {
        return NativeMeta(
            platform = "Android",
            platformVersion = Build.VERSION.RELEASE,
        )
    }
} 