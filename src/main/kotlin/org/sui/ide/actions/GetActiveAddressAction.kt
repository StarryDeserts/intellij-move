package org.sui.ide.actions

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.util.ExecUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

@Suppress("DEPRECATION")
class GetActiveAddressAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {

        val project = e.project ?: return
        val commandLine = GeneralCommandLine("sui", "client", "active-address")
        val processOutput: ProcessOutput = ExecUtil.execAndGetOutput(commandLine)

        val exitCode = processOutput.exitCode
        val output = processOutput.stdout + processOutput.stderr
        val notificationType = if (exitCode == 0) NotificationType.INFORMATION else NotificationType.ERROR
        val notification = Notification("Command Runner", "Active Address", output, notificationType)
        Notifications.Bus.notify(notification, project)
    }
}
