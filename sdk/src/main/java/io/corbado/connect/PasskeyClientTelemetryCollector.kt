package io.corbado.connect

import android.os.Build
import com.corbado.api.models.NativeMeta
import com.corbado.api.models.JavaScriptHighEntropy

internal object PasskeyClientTelemetryCollector {
    fun collectData(): NativeMeta {
        // This is a placeholder implementation.
        // We can add more detailed telemetry data later if needed.
        return NativeMeta(
            platform = "Android",
            platformVersion = Build.VERSION.RELEASE,
            isMobile = true,
            isDesktop = false,
            jsHighEntropy = JavaScriptHighEntropy()
        )
    }
} 