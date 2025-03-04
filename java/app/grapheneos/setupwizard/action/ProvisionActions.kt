package app.grapheneos.setupwizard.action

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE
import android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_TRIGGER
import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PersistableBundle
import android.provider.Settings
import android.util.Log
import app.grapheneos.setupwizard.R
import app.grapheneos.setupwizard.view.activity.WelcomeActivity

object ProvisionActions {
    private const val TAG = "ProvisionActions"
    private const val PROVISIONING_TRIGGER_QR_CODE = 2

    // Copied from ManagedProvisioning app, as they're hidden;
    private const val PROVISION_FINALIZATION_INSIDE_SUW =
        "android.app.action.PROVISION_FINALIZATION_INSIDE_SUW"
    private const val RESULT_CODE_PROFILE_OWNER_SET = 122
    private const val RESULT_CODE_DEVICE_OWNER_SET = 123

    const val REQUEST_CODE_STEP1 = 42
    const val REQUEST_CODE_STEP2_PO = 43
    const val REQUEST_CODE_STEP2_DO = 44

    fun provisionDeviceOwner(context: Activity) {
        val provisionIntent = Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
        provisionIntent.putExtra(EXTRA_PROVISIONING_TRIGGER, PROVISIONING_TRIGGER_QR_CODE)
        provisionIntent.putExtra(
            DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
            context.intent.getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME) as ComponentName?
        )
        provisionIntent.putExtra(
            DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM,
            context.intent.getStringExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM)
        )
        val systemAppsEnabled = context.intent.getBooleanExtra(DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED, false)
        if (systemAppsEnabled) {
            provisionIntent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED, true)
        }
        val skipEncryption = context.intent.getBooleanExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, false)
        if (skipEncryption) {
            provisionIntent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true)
        }
        val extrasBundle: PersistableBundle? = context.intent.getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE)
        if (extrasBundle != null) {
            provisionIntent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, extrasBundle)
        }
        context.startActivityForResult(provisionIntent, REQUEST_CODE_STEP1)
    }

    private fun disableSelfAndFinish(context: Activity) {
        // remove this activity from the package manager.
        val pm: PackageManager = context.getPackageManager()
        val name = ComponentName(context, WelcomeActivity::class.java)
        Log.i(TAG, "Disabling itself ($name)")
        pm.setComponentEnabledSetting(
            name, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        // terminate the activity.
        context.finish()
    }

    fun handleProvisioningStep1Result(context: Activity, resultCode: Int) {
        val requestCodeStep2: Int
        when (resultCode) {
            RESULT_CODE_PROFILE_OWNER_SET -> /*requestCodeStep2 = REQUEST_CODE_STEP2_PO */ {
                factoryReset(context, "profile owner is not supported")
                return
            }
            RESULT_CODE_DEVICE_OWNER_SET -> requestCodeStep2 = REQUEST_CODE_STEP2_DO
            else -> {
                factoryReset(context, "invalid response from the provisioning engine: "
                            + resultCodeToString(resultCode))
                return
            }
        }
        val intent = Intent(PROVISION_FINALIZATION_INSIDE_SUW)
                .addCategory(Intent.CATEGORY_DEFAULT)
        Log.i(TAG, "Finalizing DPC with $intent")
        context.startActivityForResult(intent, requestCodeStep2)
    }

    fun handleProvisioningStep2Result(context: Activity, requestCode: Int, resultCode: Int) {
        // Must set state before launching the intent that finalize the DPC, because the DPC
        // implementation might not remove the back button
        setProvisioningState(context)
        val doMode = requestCode == REQUEST_CODE_STEP2_DO
        if (resultCode != Activity.RESULT_OK) {
            factoryReset(context, "invalid response from the provisioning engine: "
                        + resultCodeToString(resultCode))
            return
        }
        Log.i(TAG, (if (doMode) "Device owner" else "Profile owner") + " mode provisioned!")
        disableSelfAndFinish(context)
    }

    fun resultCodeToString(resultCode: Int): String {
        val result = StringBuilder()
        when (resultCode) {
            Activity.RESULT_OK -> result.append("RESULT_OK")
            Activity.RESULT_CANCELED -> result.append("RESULT_CANCELED")
            Activity.RESULT_FIRST_USER -> result.append("RESULT_FIRST_USER")
            RESULT_CODE_PROFILE_OWNER_SET -> result.append("RESULT_CODE_PROFILE_OWNER_SET")
            RESULT_CODE_DEVICE_OWNER_SET -> result.append("RESULT_CODE_DEVICE_OWNER_SET")
            else -> result.append("UNKNOWN_CODE")
        }
        return result.append('(').append(resultCode).append(')').toString()
    }

    private fun factoryReset(context: Activity, reason: String) {
        AlertDialog.Builder(context)
            .setMessage("Device provisioning failed (" + reason
                        + ") and device must be factory reset"
            )
            .setPositiveButton(context.getString(R.string.button_reset)) { _: DialogInterface?, _: Int ->
                sendFactoryResetIntent(context, reason)
            }
            .setOnDismissListener { _: DialogInterface? ->
                sendFactoryResetIntent(context, reason)
            }
            .show()
    }

    private fun sendFactoryResetIntent(context: Activity, reason: String) {
        Log.e(TAG, "Factory resetting: $reason")
        val intent = Intent(Intent.ACTION_FACTORY_RESET)
        intent.setPackage("android")
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        intent.putExtra(Intent.EXTRA_REASON, reason)
        context.sendBroadcast(intent)

        // Just in case the factory reset request fails...
        setProvisioningState(context)
        disableSelfAndFinish(context)
    }

    private fun setProvisioningState(context: Activity) {
        Log.i(TAG, "Setting provisioning state")
        // Add a persistent setting to allow other apps to know the device has been provisioned.
        Settings.Global.putInt(context.getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 1)
        Settings.Secure.putInt(context.getContentResolver(), Settings.Secure.USER_SETUP_COMPLETE, 1)
    }

}
