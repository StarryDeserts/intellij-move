package org.sui.cli.runConfigurations

import com.intellij.execution.Executor
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.execution.ParametersListUtil
import org.gradle.internal.impldep.org.testng.SuiteRunState
import org.jdom.Element
import org.sui.cli.readPath
import org.sui.cli.readString
import org.sui.cli.runConfigurations.CommandConfigurationBase.CleanConfiguration.Companion.configurationError
import org.sui.cli.runConfigurations.test.AptosTestConsoleProperties.Companion.TEST_TOOL_WINDOW_SETTING_KEY
import org.sui.cli.runConfigurations.test.SuiTestRunState
import org.sui.cli.settings.suiExecPath
import org.sui.cli.writePath
import org.sui.cli.writeString
import org.sui.stdext.exists
import java.nio.file.Path

abstract class CommandConfigurationBase(
    project: Project,
    factory: ConfigurationFactory
) :
    LocatableConfigurationBase<SuiteRunState>(project, factory),
    RunConfigurationWithSuppressedDefaultDebugAction {

    var command: String = ""
    var workingDirectory: Path? = null
    var environmentVariables: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT

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

    @Throws(RuntimeConfigurationException::class)
    override fun checkConfiguration() {
        val config = clean()
        if (config is CleanConfiguration.Err) {
            throw config.error
        }
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): SuiRunStateBase? {
        val config = clean().ok ?: return null
        return if (showTestToolWindow(config.cmd)) {
            SuiTestRunState(environment, this, config)
        } else {
            SuiRunState(environment, this, config)
        }
    }

    fun clean(): CleanConfiguration {
        val workingDirectory = workingDirectory
            ?: return configurationError("No working directory specified")
        val (subcommand, arguments) = parseAptosCommand(command)
            ?: return configurationError("No subcommand specified")

        val aptosPath = project.suiExecPath ?: return configurationError("No Aptos CLI specified")
        if (!aptosPath.exists()) {
            return configurationError("Invalid Aptos CLI location: $aptosPath")
        }
        val commandLine =
            AptosCommandLine(
                subcommand,
                arguments,
                workingDirectory,
                environmentVariables
            )
        return CleanConfiguration.Ok(aptosPath, commandLine)
    }

    protected fun showTestToolWindow(commandLine: AptosCommandLine): Boolean =
        when {
            !AdvancedSettings.getBoolean(TEST_TOOL_WINDOW_SETTING_KEY) -> false
            commandLine.subCommand != "move test" -> false
//            "--nocapture" in commandLine.additionalArguments -> false
//            Cargo.TEST_NOCAPTURE_ENABLED_KEY.asBoolean() -> false
//            else -> !hasRemoteTarget
            else -> true
        }

    sealed class CleanConfiguration {
        class Ok(val aptosPath: Path, val cmd: AptosCommandLine) : CleanConfiguration()
        class Err(val error: RuntimeConfigurationError) : CleanConfiguration()

        val ok: Ok? get() = this as? Ok

        companion object {
            fun configurationError(@NlsContexts.DialogMessage message: String) = Err(
                RuntimeConfigurationError(message)
            )
        }
    }

    companion object {
        fun parseAptosCommand(rawAptosCommand: String): Pair<String, List<String>>? {
            val args = ParametersListUtil.parse(rawAptosCommand)
            val rootCommand = args.firstOrNull() ?: return null
            return if (rootCommand == "move") {
                val subcommand = args.drop(1).firstOrNull() ?: return null
                val command = "move $subcommand"
                val additionalArguments = args.drop(2)
                Pair(command, additionalArguments)
            } else {
                val additionalArguments = args.drop(1)
                Pair(rootCommand, additionalArguments)
            }
        }
    }
}
