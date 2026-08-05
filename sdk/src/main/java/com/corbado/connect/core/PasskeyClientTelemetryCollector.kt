package com.corbado.connect.core

import android.app.KeyguardManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.DisplayMetrics
import androidx.biometric.BiometricManager
import androidx.core.content.getSystemService
import com.corbado.connect.api.models.NativeMeta
import com.corbado.connect.api.models.NativeMetaScreen
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import java.time.Instant
import java.util.Locale

internal object PasskeyClientTelemetryCollector {
    fun collectData(context: Context, sdkInitTime: Instant): NativeMeta {
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
                androidApiLevel = Build.VERSION.SDK_INT,
                googlePlayServicesVersion = getGmsVersion(context),
                displayName = getAppLabel(context),
                brand = Build.BRAND,
                model = Build.MODEL + "|" + Build.HARDWARE + "|" + Build.DEVICE + "|" + Build.PRODUCT,
                locale = Locale.getDefault().language + "-" + Locale.getDefault().country,
                screen = getScreenData(context),
                sdkInitTimeMs = sdkInitTime.toEpochMilli()
            )
        } catch (e: Throwable) {
            // Global fail-safe: catch Throwable to prevent app crashes on exotic
            // Android environments that might throw Errors during telemetry collection.
            NativeMeta(
                platform = "Android",
                platformVersion = Build.VERSION.RELEASE,
                displayName = "",
                error = "Failed to collect telemetry: ${e.message}"
            )
        }
    }

    private fun getScreenData(context: Context): NativeMetaScreen {
        val metrics: DisplayMetrics = context.resources.displayMetrics

        val densityFactor = if (metrics.density > 0f) metrics.density else 1f
        val widthPixels = metrics.widthPixels
        val heightPixels = metrics.heightPixels
        val widthDp = (widthPixels / densityFactor).toFloat()
        val heightDp = (heightPixels / densityFactor).toFloat()

        return NativeMetaScreen(
            widthDp,
            heightDp,
            densityFactor.toFloat()
        )
    }

    private fun getAppVersion(context: Context): String? {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return packageInfo.versionName
    }

    private fun getAppBuild(context: Context): String? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toString()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getDeviceOwnerAuth(context: Context): NativeMeta.DeviceOwnerAuth {
        return try {
            when (BiometricManager.from(context)
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
        } catch (_: Throwable) {
            NativeMeta.DeviceOwnerAuth.none
        }
    }

    private fun isBluetoothAvailable(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
    }

    private fun isBluetoothOn(context: Context): Boolean? {
        return try {
            val bluetoothManager = context.getSystemService<BluetoothManager>()
            val bluetoothAdapter = bluetoothManager?.adapter
            bluetoothAdapter?.isEnabled
        } catch (_: Throwable) {
            // Catching Throwable instead of Exception because rare OEM builds with
            // broken or customized Bluetooth stacks can throw low-level Errors.
            null
        }
    }

    private fun isGooglePlayServicesAvailable(context: Context): Boolean? {
        return try {
            val googleApiAvailability = GoogleApiAvailability.getInstance()
            val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
            resultCode == ConnectionResult.SUCCESS
        } catch (_: Throwable) {
            // Use Throwable to safely handle exotic devices/ROMs where Play Services
            // might be stripped or stubbed in ways that throw low-level Errors.
            null
        }
    }

    private fun getGmsVersion(context: Context): String? {
        return try {
            context.packageManager.getPackageInfo("com.google.android.gms", 0).versionName
        } catch (_: Throwable) {
            // Catching Throwable handles cases where the Package Manager service is dead,
            // the package is invisible (Android 11+), or exotic ROMs return null.
            null
        }
    }

    fun getAppLabel(context: Context): String {
        return try {
            val packageManager = context.packageManager
            val applicationInfo = packageManager.getApplicationInfo(context.packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (_: Exception) {
            ""
        }
    }
}
