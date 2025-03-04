package app.grapheneos.setupwizard.view.activity

import android.app.Activity
import android.os.Bundle
import app.grapheneos.setupwizard.action.ProvisionActions

class ProvisionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProvisionActions.provisionDeviceOwner(this)
    }
}