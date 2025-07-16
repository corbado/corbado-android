package com.corbado.connect.core

import android.app.KeyguardManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.core.content.getSystemService
import com.corbado.connect.api.models.NativeMeta
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

internal object PasskeyClientTelemetryCollector {
    fun collectData(context: Context): NativeMeta {
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
                displayName = getAppLabel(context),
            )
        } catch (e: Exception) {
            NativeMeta(
                platform = "Android",
                platformVersion = Build.VERSION.RELEASE,
                displayName = "",
                error = "Failed to collect telemetry: ${e.message}"
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
                val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE)
                        as? KeyguardManager
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
        val pkgInfo = context.packageManager.getPackageInfo("com.google.android.gms", 0)
        val playServicesVersion = if (Build.VERSION.SDK_INT >= 28)
            pkgInfo.longVersionCode
        else
            @Suppress("DEPRECATION") pkgInfo.versionCode.toLong()
        return resultCode == ConnectionResult.SUCCESS
    }

    fun getAppLabel(context: Context): String {
        val packageManager = context.packageManager
        val applicationInfo = packageManager.getApplicationInfo(context.packageName, 0)
        return packageManager.getApplicationLabel(applicationInfo).toString()
    }
}