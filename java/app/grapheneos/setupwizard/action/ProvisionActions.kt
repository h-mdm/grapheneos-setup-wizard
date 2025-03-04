package app.grapheneos.setupwizard.action

import android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE
import android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_TRIGGER
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.util.Log

object ProvisionActions {
    private const val TAG = "ProvisionActions"
    private const val PROVISIONING_TRIGGER_QR_CODE = 2

    // Copied from ManagedProvisioning app, as they're hidden;
    private const val PROVISION_FINALIZATION_INSIDE_SUW =
        "android.app.action.PROVISION_FINALIZATION_INSIDE_SUW"
    private const val RESULT_CODE_PROFILE_OWNER_SET = 122
    private const val RESULT_CODE_DEVICE_OWNER_SET = 123

    private const val REQUEST_CODE_STEP1 = 42
    private const val REQUEST_CODE_STEP2_PO = 43
    private const val REQUEST_CODE_STEP2_DO = 44

    fun provisionDeviceOwner(context: Activity) {
        val provisionIntent = Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
        provisionIntent.putExtra(EXTRA_PROVISIONING_TRIGGER, PROVISIONING_TRIGGER_QR_CODE)
        provisionIntent.putExtra(
            DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
            context.intent.getStringExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME)
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
        val extrasBundle = context.intent.getBundleExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE)
        if (extrasBundle != null) {
            provisionIntent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, extrasBundle)
        }
        context.startActivityForResult(provisionIntent, REQUEST_CODE_STEP1)
    }

    private fun setProvisioningState(context: Activity) {
        Log.i(TAG, "Setting provisioning state")
        // Add a persistent setting to allow other apps to know the device has been provisioned.
        //Settings.Global.putInt(context.getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 1)
        //Settings.Secure.putInt(context.getContentResolver(), Settings.Secure.USER_SETUP_COMPLETE, 1)
    }

}