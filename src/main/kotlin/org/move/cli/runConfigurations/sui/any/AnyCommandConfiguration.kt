package org.move.cli.runConfigurations.sui.any

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.project.Project
import org.move.cli.moveProjects
import org.move.cli.runConfigurations.sui.CommandConfigurationBase

class AnyCommandConfiguration(
    project: Project,
    factory: ConfigurationFactory
) :
    CommandConfigurationBase(project, factory) {

    init {
        workingDirectory = if (!project.isDefault) {
            project.moveProjects.allProjects.firstOrNull()?.contentRootPath
        } else {
            null
        }
    }

    override fun getConfigurationEditor() = AnyCommandConfigurationEditor()
}
