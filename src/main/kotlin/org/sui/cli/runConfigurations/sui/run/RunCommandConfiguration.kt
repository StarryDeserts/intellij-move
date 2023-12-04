package org.sui.cli.runConfigurations.sui.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.project.Project
import org.sui.cli.runConfigurations.sui.run.TestCommandConfiguration
import org.sui.cli.runConfigurations.sui.run.TestCommandConfigurationHandler
import org.sui.cli.moveProjects
import org.sui.cli.runConfigurations.sui.FunctionCallConfigurationBase
import org.sui.cli.runConfigurations.sui.FunctionCallConfigurationEditor

class RunCommandConfiguration(
    project: Project,
    factory: ConfigurationFactory
) : FunctionCallConfigurationBase(project, factory, TestCommandConfigurationHandler()) {
    override var command: String = "move run"
    override fun getConfigurationEditor(): FunctionCallConfigurationEditor<TestCommandConfiguration> {
        val moveProject = project.moveProjects.allProjects.first()
        val editor = FunctionCallConfigurationEditor<TestCommandConfiguration>(
            TestCommandConfigurationHandler(),
            moveProject,
        )
        return editor
    }
}
