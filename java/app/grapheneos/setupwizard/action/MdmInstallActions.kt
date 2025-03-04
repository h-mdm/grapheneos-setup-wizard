package app.grapheneos.setupwizard.action

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE
import android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME
import android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM
import android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED
import android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.wifi.WifiConfiguration
import android.os.PersistableBundle
import android.text.TextUtils
import android.util.Base64
import androidx.appcompat.app.AlertDialog
import app.grapheneos.setupwizard.R
import app.grapheneos.setupwizard.data.MdmInstallData
import app.grapheneos.setupwizard.view.activity.ProvisionActivity
import app.grapheneos.setupwizard.view.activity.SetupWizardActivity
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.android.setupcompat.util.SystemBarHelper
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import android.net.wifi.WifiManager
import android.util.Log


object MdmInstallActions {
    private const val TAG = "MdmInstallActions"
    const val EXTRA_QR_CONTENTS = "EXTRA_QR_CONTENTS"

    private const val EXTRA_ADMIN_COMPONENT_NAME = "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME"
    private const val EXTRA_DOWNLOAD_LOCATION = "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION"
    private const val EXTRA_PACKAGE_CHECKSUM = "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM"
    private const val EXTRA_SKIP_ENCRYPTION = "android.app.extra.PROVISIONING_SKIP_ENCRYPTION"
    private const val EXTRA_SYSTEM_APPS_ENABLED = "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED"
    private const val EXTRA_EXTRAS_BUNDLE = "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE"
    private const val EXTRA_WIFI_SSID = "android.app.extra.PROVISIONING_WIFI_SSID"
    private const val EXTRA_WIFI_PASSWORD = "android.app.extra.PROVISIONING_WIFI_PASSWORD"
    private const val EXTRA_WIFI_SECURITY_TYPE = "android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE"

    private var adminComponentName: String? = null
    private var downloadLocation: String? = null
    private var packageChecksum: String? = null
    private var skipEncryption: Boolean = false
    private var systemAppsEnabled: Boolean = false
    private var extrasBundle: PersistableBundle? = null
    private var wifiSsid: String? = null
    private var wifiPassword: String? = null
    private var wifiSecurityType: String? = null

    private val executor = Executors.newSingleThreadExecutor()
    private const val CONNECTION_TIMEOUT_MS = 10000;
    private const val ADMIN_APK_FILE_NAME = "deviceadmin.apk"
    private var calculatedPackageChecksum: String? = null

    fun handleEntry(context: Activity) {
        SystemBarHelper.setBackButtonVisible(context.window, false)
        val qrContent = context.intent.getStringExtra(EXTRA_QR_CONTENTS)
        if (qrContent == null) {
            MdmInstallData.error.postValue(context.getString(R.string.qr_parse_failed))
        }
        if (!parseProvisioningQr(context, qrContent!!)) {
            return
        }
        setupWiFi(context)
    }

    fun handleError(context: Activity, message: String) {
        try {
            context.unregisterReceiver(appInstallReceiver)
        } catch (e: Exception) {
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.error_title)
            .setMessage(message)
            .setPositiveButton(R.string.button_ok) { dialog, _ ->
                dialog.dismiss()
                MdmInstallData.error.postValue(null)
                context.finish()
            }
            .setCancelable(false)
            .create()
            .show()
    }

    private fun parseProvisioningQr(context: Activity, qrContent: String): Boolean {
        val objectMapper = ObjectMapper()
        lateinit var jsonNode: JsonNode
        try {
            jsonNode = objectMapper.readTree(qrContent)
        } catch(e: Exception) {
            MdmInstallData.error.postValue(context.getString(R.string.qr_parse_failed))
            return false
        }

        if (jsonNode.has(EXTRA_ADMIN_COMPONENT_NAME) && jsonNode[EXTRA_ADMIN_COMPONENT_NAME].isTextual) {
            adminComponentName = jsonNode[EXTRA_ADMIN_COMPONENT_NAME].asText()
        } else {
            MdmInstallData.error.postValue(context.getString(R.string.qr_missing_parameter) + EXTRA_ADMIN_COMPONENT_NAME)
            return false
        }

        if (jsonNode.has(EXTRA_DOWNLOAD_LOCATION) && jsonNode[EXTRA_DOWNLOAD_LOCATION].isTextual) {
            downloadLocation = jsonNode[EXTRA_DOWNLOAD_LOCATION].asText()
        } else {
            MdmInstallData.error.postValue(context.getString(R.string.qr_missing_parameter) + EXTRA_DOWNLOAD_LOCATION)
            return false
        }

        if (jsonNode.has(EXTRA_PACKAGE_CHECKSUM) && jsonNode[EXTRA_PACKAGE_CHECKSUM].isTextual) {
            packageChecksum = jsonNode[EXTRA_PACKAGE_CHECKSUM].asText()
        } else {
            MdmInstallData.error.postValue(context.getString(R.string.qr_missing_parameter) + EXTRA_PACKAGE_CHECKSUM)
            return false
        }

        if (jsonNode.has(EXTRA_SKIP_ENCRYPTION) && jsonNode[EXTRA_SKIP_ENCRYPTION].isBoolean) {
            skipEncryption = jsonNode[EXTRA_SKIP_ENCRYPTION].asBoolean()
        }

        if (jsonNode.has(EXTRA_SYSTEM_APPS_ENABLED) && jsonNode[EXTRA_SYSTEM_APPS_ENABLED].isBoolean) {
            systemAppsEnabled = jsonNode[EXTRA_SYSTEM_APPS_ENABLED].asBoolean()
        }

        if (jsonNode.has(EXTRA_EXTRAS_BUNDLE) && jsonNode[EXTRA_EXTRAS_BUNDLE].isObject) {
            extrasBundle = jsonToPersistableBundle(jsonNode[EXTRA_EXTRAS_BUNDLE])
        }

        if (jsonNode.has(EXTRA_WIFI_SSID) && jsonNode[EXTRA_WIFI_SSID].isTextual) {
            wifiSsid = jsonNode[EXTRA_WIFI_SSID].asText()
        }

        if (jsonNode.has(EXTRA_WIFI_PASSWORD) && jsonNode[EXTRA_WIFI_PASSWORD].isTextual) {
            wifiPassword = jsonNode[EXTRA_WIFI_PASSWORD].asText()
        }

        if (jsonNode.has(EXTRA_WIFI_SECURITY_TYPE) && jsonNode[EXTRA_WIFI_SECURITY_TYPE].isTextual) {
            wifiSecurityType = jsonNode[EXTRA_WIFI_SECURITY_TYPE].asText()
        }

        return true
    }

    private fun setupWiFi(context: Activity) {
        if (wifiSsid == null) {
            setupWiFiManual(context)
        } else {
            setupWiFiAutomatic(context)
        }
    }

    private fun setupWiFiManual(context: Activity) {
        WifiActions.launchSetup(context as SetupWizardActivity)
    }

    private fun setupWiFiAutomatic(context: Activity) {
        MdmInstallData.spinnerVisible.postValue(true)
        MdmInstallData.message.postValue(context.getString(R.string.setting_up_wifi))

        val wifiManager = context.getSystemService(WifiManager::class.java)
        if (wifiManager == null) {
            Log.e(TAG, "Failed to retrieve the WifiManager service")
            MdmInstallData.error.postValue(context.getString(R.string.wifi_failed))
            return
        }

        val wifiConfiguration = WifiConfiguration()
        wifiConfiguration.SSID = "\"" + wifiSsid + "\"";

        when(wifiSecurityType) {
            "NONE" -> {
                wifiConfiguration.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN);
            }
            "WPA" -> {
                wifiConfiguration.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK)
                if (!TextUtils.isEmpty(wifiPassword)) {
                    if (wifiPassword?.matches("[0-9A-Fa-f]{64}".toRegex()) == true) {
                        wifiConfiguration.preSharedKey = wifiPassword
                    } else {
                        wifiConfiguration.preSharedKey = "\"" + wifiPassword + "\""
                    }
                }
            }
            "EAP" -> {
                wifiConfiguration.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
                if (!TextUtils.isEmpty(wifiPassword)) {
                    wifiConfiguration.preSharedKey = "\"" + wifiPassword + "\""
                }
            }
            "WEP" -> {
                wifiConfiguration.setSecurityParams(WifiConfiguration.SECURITY_TYPE_WEP)
                if (!TextUtils.isEmpty(wifiPassword)) {
                    val length: Int = wifiPassword!!.length
                    // WEP-40, WEP-104, and 256-bit WEP (WEP-232?)
                    if ((length == 10 || length == 26 || length == 58)
                        && wifiPassword?.matches("[0-9A-Fa-f]*".toRegex()) == true
                    ) {
                        wifiConfiguration.wepKeys[0] = wifiPassword
                    } else {
                        wifiConfiguration.wepKeys[0] = "\"" + wifiPassword + "\""
                    }
                }

            }
            else -> {
                MdmInstallData.error.postValue("Unsupported WiFi security type: " + wifiSecurityType)
                return
            }
        }

        wifiManager.connect(wifiConfiguration, object: WifiManager.ActionListener() {
            override fun onSuccess() {
                MdmInstallData.spinnerVisible.postValue(false)
                onWifiSetupComplete(context)
            }

            override fun onFailure(reason: Int) {
                MdmInstallData.spinnerVisible.postValue(false)
                Log.e(TAG, "Failed to set up WiFi: " + reason)
                MdmInstallData.error.postValue(context.getString(R.string.wifi_failed))
                return
            }
        })
    }

    private fun onWifiSetupComplete(context: Activity) {
        downloadAdminApp(context)
    }

    private fun downloadAdminApp(context: Activity) {
        MdmInstallData.message.postValue(context.getString(R.string.downloading_admin_app))
        executor.execute {
            if (downloadAdminAppSync(context)) {
                MdmInstallData.progressVisible.postValue(false)
                if (!calculatedPackageChecksum.equals(packageChecksum, ignoreCase = true)) {
                    MdmInstallData.error.postValue(context.getString(R.string.checksum_failed))
                    return@execute
                }
                installAdminApp(context)
            }
        }
    }

    private fun downloadAdminAppSync(context: Activity) : Boolean {
        var tempFile = File(context.filesDir, ADMIN_APK_FILE_NAME)
        if (tempFile.exists()) {
            tempFile.delete()
        }
        try {
            try {
                tempFile.createNewFile()
            } catch (e: Exception) {
                e.printStackTrace()
                MdmInstallData.error.postValue("Failed to create " + tempFile.absolutePath)
                return false
            }
            val url = URL(downloadLocation)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.connectTimeout = CONNECTION_TIMEOUT_MS
            connection.readTimeout = CONNECTION_TIMEOUT_MS
            connection.connect()
            if (connection.responseCode != 200) {
                throw java.lang.Exception("Bad server response for " + downloadLocation + ": " + connection.responseCode)
            }
            val lengthOfFile = connection.contentLength
            notifyDownloadStart(lengthOfFile)
            val digest = MessageDigest.getInstance("SHA-256")
            val dis = DataInputStream(connection.inputStream)
            val buffer = ByteArray(1024)
            var length: Int
            var total: Long = 0
            val fos = FileOutputStream(tempFile)
            while (dis.read(buffer).also { length = it } > 0) {
                digest.update(buffer, 0, length);
                total += length.toLong()
                notifyDownloadProgress(context, total.toInt(), lengthOfFile)
                fos.write(buffer, 0, length)
            }
            fos.flush()
            fos.close()
            dis.close()
            calculatedPackageChecksum = Base64.encodeToString(digest.digest(), Base64.NO_WRAP or Base64.URL_SAFE)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            tempFile.delete()
            MdmInstallData.error.postValue(context.getString(R.string.download_failed) + e.message)
            return false
        }
        return true
    }

    private fun notifyDownloadStart(total: Int) {
        if (total == -1) {
            // We don't know the content length
            MdmInstallData.spinnerVisible.postValue(true)
            MdmInstallData.progressVisible.postValue(false)
        } else {
            MdmInstallData.spinnerVisible.postValue(false)
            MdmInstallData.progressVisible.postValue(true)
        }
    }

    private fun notifyDownloadProgress(context: Activity, downloaded: Int, total: Int) {
        if (total != -1) {
            val downloadedMb: Float = downloaded / 1048576.0f
            val totalMb: Float = total / 1048576.0f
            val progress = context.getString(R.string.download_progress, downloadedMb, totalMb)
            MdmInstallData.downloadProgressLegend.postValue(progress)
            MdmInstallData.downloadProgress.postValue((downloadedMb * 100 / totalMb).toInt())
        }
    }

    val appInstallReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent!!.getIntExtra(PackageInstaller.EXTRA_STATUS, 0)
            when (status) {
                PackageInstaller.STATUS_SUCCESS -> {
                    MdmInstallData.spinnerVisible.postValue(false)
                    MdmInstallData.message.postValue(context?.getString(R.string.install_successful))
                    MdmInstallData.complete.postValue(true)
                }
                else -> {
                    // Installation failure
                    val extraMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    val statusMessage = AppInstaller.getPackageInstallerStatusMessage(status)
                    var errorText = context?.getString(R.string.install_failed) + statusMessage
                    if (extraMessage != null && extraMessage.length > 0) {
                        errorText += ", extra: $extraMessage"
                    }
                    MdmInstallData.error.postValue(errorText)
                }
            }
        }
    }

    private fun installAdminApp(context: Activity) {
        MdmInstallData.spinnerVisible.postValue(true)
        MdmInstallData.message.postValue(context.getString(R.string.installing_admin_app))
        context.registerReceiver(
            appInstallReceiver,
            IntentFilter(AppInstaller.ACTION_INSTALL_COMPLETE),
            Context.RECEIVER_EXPORTED
        )
        executor.execute {
            val error = AppInstaller.silentInstallApplication(context, File(context.filesDir, ADMIN_APK_FILE_NAME))
            if (error != null) {
                context.unregisterReceiver(appInstallReceiver)
                MdmInstallData.error.postValue(context.getString(R.string.install_failed) + error)
            }
            // Install completion is caught in the BroadcastReceiver
        }
    }

    fun provisionDeviceOwner(context: Activity) {
        try {
            context.unregisterReceiver(appInstallReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN)) {
            handleError(context, "Cannot set up device owner because device does not have the "
                    + PackageManager.FEATURE_DEVICE_ADMIN + " feature")
            return
        }
        val dpm: DevicePolicyManager? = context.getSystemService(DevicePolicyManager::class.java)
        if (dpm == null) {
            handleError(context, "Cannot set up device owner because DevicePolicyManager can't be initialized")
            return
        }
        if (!dpm.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE)) {
            handleError(context, "DeviceOwner provisioning is not allowed, most like device is already provisioned")
            return
        }

        val intent = Intent(context, ProvisionActivity::class.java)

        val adminComponentNameParts = adminComponentName!!.split("/")
        if (adminComponentNameParts.size != 2) {
            handleError(context, "Wrong component name format: " + adminComponentName)
            return
        }
        intent.putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
            ComponentName(adminComponentNameParts[0], adminComponentNameParts[1]))
        intent.putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM, packageChecksum)
        if (systemAppsEnabled) {
            intent.putExtra(EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED, true)
        }
        if (skipEncryption) {
            intent.putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, true)
        }
        if (extrasBundle != null) {
            intent.putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, extrasBundle)
        }
        context.startActivity(intent)
    }

    private fun jsonToPersistableBundle(jsonNode: JsonNode): PersistableBundle {
        val bundle = PersistableBundle()

        jsonNode.fields().forEach { (key, value) ->
            when {
                value.isTextual -> bundle.putString(key, value.asText())
                value.isInt -> bundle.putInt(key, value.asInt())
                value.isLong -> bundle.putLong(key, value.asLong())
                value.isBoolean -> bundle.putBoolean(key, value.asBoolean())
                value.isDouble -> bundle.putDouble(key, value.asDouble())
                value.isObject -> bundle.putPersistableBundle(key, jsonToPersistableBundle(value)) // Recursively handle nested objects
                value.isArray -> {
                    // Handle array of supported primitive types
                    val arrayElements = value.map { it.asText() }.toTypedArray()
                    bundle.putStringArray(key, arrayElements) // Using StringArray as PersistableBundle doesn't support generic arrays
                }
            }
        }
        return bundle
    }

    fun handleActivityResult(activity: Activity, resultCode: Int) {
        if (resultCode == Activity.RESULT_CANCELED) {
            handleError(activity, activity.getString(R.string.wifi_failed))
        } else {
            onWifiSetupComplete(activity)
        }
    }
}
