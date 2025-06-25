package io.corbado.connect

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.core.content.getSystemService
import com.corbado.api.models.NativeMeta
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

internal object PasskeyClientTelemetryCollector {
    fun collectData(context: Context): NativeMeta {
        var errorMessage: String? = null

        return try {
            NativeMeta(
                platform = "Android",
                platformVersion = Build.VERSION.RELEASE,
                name = context.packageName,
                version = getAppVersion(context),
                build = getAppBuild(context),
                deviceOwnerAuth = getDeviceOwnerAuth(context),
                isBluetoothAvailable = isBluetoothAvailable(context),
                isBluetoothOn = isBluetoothOn(context),
                isGooglePlayServices = isGooglePlayServicesAvailable(context),
            )
        } catch (e: Exception) {
            errorMessage = "Failed to collect telemetry: ${e.message}"
            NativeMeta(
                platform = "Android",
                platformVersion = Build.VERSION.RELEASE,
                error = errorMessage
            )
        }
    }

    private fun getAppVersion(context: Context): String {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return packageInfo.versionName  
    }

    private fun getAppBuild(context: Context): String {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return packageInfo.longVersionCode.toString()
        } else {
            @Suppress("DEPRECATION")
            return packageInfo.versionCode.toString()
        }
    }

    private fun getDeviceOwnerAuth(context: Context): NativeMeta.DeviceOwnerAuth {
        return when (BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> NativeMeta.DeviceOwnerAuth.biometrics
            else -> {
                // Check if device has any security (PIN, pattern, password)
                val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE)
                        as? android.app.KeyguardManager
                when {
                    keyguardManager?.isDeviceSecure == true -> NativeMeta.DeviceOwnerAuth.code
                    else -> NativeMeta.DeviceOwnerAuth.none
                }
            }
        }
    }

    private fun isBluetoothAvailable(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
    }

    private fun isBluetoothOn(context: Context): Boolean? {
        val bluetoothManager = context.getSystemService<BluetoothManager>()
        val bluetoothAdapter = bluetoothManager?.adapter
        return bluetoothAdapter?.isEnabled
    }

    private fun isGooglePlayServicesAvailable(context: Context): Boolean? {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
        return resultCode == ConnectionResult.SUCCESS
    }
}