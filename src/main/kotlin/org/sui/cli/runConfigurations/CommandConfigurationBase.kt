package org.sui.cli.runConfigurations

import com.intellij.execution.Executor
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RunConfigurationWithSuppressedDefaultDebugAction
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import org.jdom.Element
import org.sui.cli.readPath
import org.sui.cli.readString
import org.sui.cli.runConfigurations.CommandConfigurationBase.CleanConfiguration.Companion.configurationError
import org.sui.cli.runConfigurations.legacy.MoveCommandConfiguration
import org.sui.cli.writePath
import org.sui.cli.writeString
import org.sui.ide.notifications.Notifications
import org.sui.stdext.exists
import java.nio.file.Path

abstract class CommandConfigurationBase(
    project: Project,
    factory: ConfigurationFactory
) :
    LocatableConfigurationBase<MoveCommandLineState>(project, factory),
    RunConfigurationWithSuppressedDefaultDebugAction {

    var command: String = ""
    var workingDirectory: Path? = null
    var environmentVariables: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT

    abstract fun getCliPath(project: Project): Path?

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.writeString("command", this.command)
        element.writePath("workingDirectory", this.workingDirectory)
        environmentVariables.writeExternal(element)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        this.command = element.readString("command") ?: return
        this.workingDirectory = element.readPath("workingDirectory") ?: return
        this.environmentVariables = EnvironmentVariablesData.readExternal(element)
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): MoveCommandLineState? {
        val config = clean()
        when (config) {
            is CleanConfiguration.Ok -> {
                return MoveCommandLineState(environment, config.cliPath, config.commandLine)
            }

            is CleanConfiguration.Err -> {
                config.error.message?.let {
                    Notifications.pluginNotifications()
                        .createNotification("Run Configuration error", it, NotificationType.ERROR)
                        .notify(project)
                }
                return null
            }
        }
//        return clean().ok
//            ?.let { config ->
//                MoveCommandLineState(environment, config.cliPath, config.commandLine)
//            }
    }

    fun clean(): CleanConfiguration {
        val workingDirectory = workingDirectory
            ?: return configurationError("No working directory specified")
        val parsedCommand = MoveCommandConfiguration.ParsedCommand.parse(command)
            ?: return configurationError("No subcommand specified")

        val cliLocation =
            this.getCliPath(project) ?: return configurationError("No blockchain CLI specified")
        if (!cliLocation.exists()) {
            return configurationError("Invalid CLI location: $cliLocation")
        }
        val commandLine =
            CliCommandLineArgs(
                parsedCommand.command,
                parsedCommand.additionalArguments,
                workingDirectory,
                environmentVariables
            )
        return CleanConfiguration.Ok(cliLocation, commandLine)
    }

    sealed class CleanConfiguration {
        class Ok(val cliPath: Path, val commandLine: CliCommandLineArgs) : CleanConfiguration()
        class Err(val error: RuntimeConfigurationError) : CleanConfiguration()

        val ok: Ok? get() = this as? Ok

        companion object {
            fun configurationError(@NlsContexts.DialogMessage message: String) = Err(
                RuntimeConfigurationError(message)
            )
        }
    }
}
