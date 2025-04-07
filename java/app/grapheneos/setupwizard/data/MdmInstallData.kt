package app.grapheneos.setupwizard.data

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import app.grapheneos.setupwizard.action.MdmInstallActions

object MdmInstallData : ViewModel() {
    val spinnerVisible = MutableLiveData<Boolean>()
    val progressVisible = MutableLiveData<Boolean>()
    val message = MutableLiveData<String>()
    val downloadProgress = MutableLiveData<Int>()
    val downloadProgressLegend = MutableLiveData<String>()
    val error = MutableLiveData<String?>()
    val complete = MutableLiveData<Boolean>()

    init {
        MdmInstallActions
    }
}
