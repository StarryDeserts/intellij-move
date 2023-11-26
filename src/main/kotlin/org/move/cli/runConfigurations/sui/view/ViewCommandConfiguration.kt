package org.move.cli.runConfigurations.sui.view

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.project.Project
import org.move.cli.moveProjects
import org.move.cli.runConfigurations.sui.FunctionCallConfigurationBase
import org.move.cli.runConfigurations.sui.FunctionCallConfigurationEditor

class ViewCommandConfiguration(
    project: Project,
    factory: ConfigurationFactory
) : FunctionCallConfigurationBase(project, factory, ViewCommandConfigurationHandler()) {

    override fun getConfigurationEditor(): FunctionCallConfigurationEditor<ViewCommandConfiguration> {
        val moveProject = project.moveProjects.allProjects.first()
        val editor = FunctionCallConfigurationEditor<ViewCommandConfiguration>(
            ViewCommandConfigurationHandler(),
            moveProject,
        )
        return editor
    }
}
