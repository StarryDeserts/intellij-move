package org.sui.cli.runConfigurations.aptos.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.project.Project
import org.sui.cli.moveProjectsService
import org.sui.cli.runConfigurations.aptos.FunctionCallConfigurationBase
import org.sui.cli.runConfigurations.aptos.FunctionCallConfigurationEditor

class RunCommandConfiguration(
    project: Project,
    factory: ConfigurationFactory
) : FunctionCallConfigurationBase(project, factory, RunCommandConfigurationHandler()) {

    override fun getConfigurationEditor(): FunctionCallConfigurationEditor<RunCommandConfiguration> {
        val moveProject = project.moveProjectsService.allProjects.first()
        val editor = FunctionCallConfigurationEditor<RunCommandConfiguration>(
            RunCommandConfigurationHandler(),
            moveProject,
        )
        return editor
    }
}
