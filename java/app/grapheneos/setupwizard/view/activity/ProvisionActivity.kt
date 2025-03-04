package app.grapheneos.setupwizard.view.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import app.grapheneos.setupwizard.action.ProvisionActions

class ProvisionActivity : Activity() {
    private val TAG = "ProvisionActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProvisionActions.provisionDeviceOwner(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        Log.d(
            TAG, "onActivityResult(): request=" + requestCode + ", result="
                    + ProvisionActions.resultCodeToString(resultCode) + ", data=" + data
        )
        when (requestCode) {
            ProvisionActions.REQUEST_CODE_STEP1 -> ProvisionActions.handleProvisioningStep1Result(this, resultCode)

            ProvisionActions.REQUEST_CODE_STEP2_PO, ProvisionActions.REQUEST_CODE_STEP2_DO ->
                ProvisionActions.handleProvisioningStep2Result(this, requestCode, resultCode)
            else -> showErrorMessage("onActivityResult(): invalid request code $requestCode")
        }
    }

    private fun showErrorMessage(message: String) {
        Log.e(TAG, "Error: $message")
    }
}