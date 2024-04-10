package org.sui.ide.actions

import com.google.gson.Gson
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import org.sui.ide.dialog.AddressDialog

class OpenSwitchAddressDialogAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {

        val commandLine = GeneralCommandLine("sui", "client", "addresses", "--json")
        val processOutput: ProcessOutput = ExecUtil.execAndGetOutput(commandLine)

        val addressesJson = processOutput.stdout + processOutput.stderr
        val data = Gson().fromJson(addressesJson, AddressesOut::class.java)
        val addressList = data.addresses
        ApplicationManager.getApplication().invokeLater {
            AddressDialog(addressList).show()
        }
    }

}

data class AddressesOut(
    val activeAddress: String,
    val addresses: List<String>
)