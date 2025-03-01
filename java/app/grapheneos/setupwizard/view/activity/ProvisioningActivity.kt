package app.grapheneos.setupwizard.view.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import app.grapheneos.setupwizard.R
import app.grapheneos.setupwizard.action.DateTimeActions
import app.grapheneos.setupwizard.action.FinishActions
import app.grapheneos.setupwizard.action.ProvisioningActions
import app.grapheneos.setupwizard.action.SetupWizard
import app.grapheneos.setupwizard.action.WelcomeActions
import app.grapheneos.setupwizard.data.DateTimeData
import app.grapheneos.setupwizard.data.ProvisioningData
import app.grapheneos.setupwizard.utils.DebugFlags

class ProvisioningActivity : SetupWizardActivity(
    R.layout.activity_provisioning,
    R.drawable.baseline_provisioning_glif,
    R.string.provisioning_title,
    R.string.provisioning_desc,
) {
    companion object {
        private const val TAG = "ProvisioningActivity"
    }

    private lateinit var spinner: ProgressBar
    private lateinit var message: TextView
    private lateinit var linearProgress: ProgressBar
    private lateinit var progressLegend: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProvisioningActions.handleEntry(this)
    }

    override fun onResume() {
        super.onResume()
        DateTimeActions.handleEntry()
    }

    override fun onPause() {
        super.onPause()
        DateTimeActions.handleExit()
    }

    override fun onActivityResult(resultCode: Int, data: Intent?) {
        super.onActivityResult(resultCode, data)
        ProvisioningActions.handleActivityResult(this, resultCode, data)
    }

    override fun bindViews() {
        spinner = requireViewById(R.id.spinning_progress)
        message = requireViewById(R.id.text_message)
        linearProgress = requireViewById(R.id.linear_progress)
        progressLegend = requireViewById(R.id.progress_legend)
        secondaryButton.visibility = View.GONE
        primaryButton.visibility = View.GONE

        ProvisioningData.message.observe(this) {
            this.message.text = it
        }
        ProvisioningData.spinnerVisible.observe(this) {
            this.spinner.visibility = if (it) View.VISIBLE else View.GONE
        }
        ProvisioningData.progressVisible.observe(this) {
            val visibility = if (it) View.VISIBLE else View.GONE
            this.linearProgress.visibility = visibility
            this.progressLegend.visibility = visibility
        }
        ProvisioningData.downloadProgress.observe(this) {
            this.linearProgress.progress = it
        }
        ProvisioningData.downloadProgressLegend.observe(this) {
            this.progressLegend.text = it
        }
        ProvisioningData.error.observe(this) {
            if (it != null) {
                ProvisioningActions.handleError(this, it)
            }
        }
        ProvisioningData.complete.observe(this) {
            primaryButton.setText(this, R.string.next)
            primaryButton.visibility = View.VISIBLE
        }
    }

    override fun setupActions() {
        primaryButton.setOnClickListener {
            ProvisioningActions.provisionDeviceOwner(this)
        }
    }
}
