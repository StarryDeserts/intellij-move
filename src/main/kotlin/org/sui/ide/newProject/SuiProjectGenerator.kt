package org.sui.ide.newProject

import com.intellij.ide.util.projectWizard.AbstractNewProjectStep
import com.intellij.ide.util.projectWizard.CustomStepProjectGenerator
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.welcomeScreen.AbstractActionWithPanel
import com.intellij.platform.DirectoryProjectGenerator
import com.intellij.platform.DirectoryProjectGeneratorBase
import com.intellij.platform.ProjectGeneratorPeer
import org.sui.cli.PluginApplicationDisposable
import org.sui.cli.defaultMoveSettings
import org.sui.cli.moveProjects
import org.sui.cli.runConfigurations.addDefaultBuildRunConfiguration
import org.sui.cli.settings.SuiSettingsPanel
import org.sui.cli.settings.moveSettings
import org.sui.ide.MoveIcons
import org.sui.ide.notifications.updateAllNotifications
import org.sui.openapiext.computeWithCancelableProgress
import org.sui.stdext.unwrapOrThrow

data class SuiProjectConfig(
    val panelData: SuiSettingsPanel.PanelData,
)

class SuiProjectGenerator: DirectoryProjectGeneratorBase<SuiProjectConfig>(),
                             CustomStepProjectGenerator<SuiProjectConfig> {

    private val disposable = service<PluginApplicationDisposable>()

    override fun getName() = "Sui"
    override fun getLogo() = MoveIcons.MOVE_LOGO
    override fun createPeer(): ProjectGeneratorPeer<SuiProjectConfig> = SuiProjectGeneratorPeer(disposable)

    /**
     * This method is called when the user clicks "Create" in the New Project dialog.
     * It is called on the EDT, so it should not block.
     */
    override fun generateProject(
        project: Project,
        baseDir: VirtualFile,
        projectConfig: SuiProjectConfig,
        module: Module
    ) {
        val suiExecutor = projectConfig.panelData.suiExec.toExecutor() ?: return
        val packageName = project.name

        val manifestFile =
            project.computeWithCancelableProgress("Generating Sui Project...") {
                val manifestFile = suiExecutor.moveNew(
                    project,
                    disposable,
                    rootDirectory = baseDir,
                    packageName = packageName
                )
                    .unwrapOrThrow() // TODO throw? really??

                manifestFile
            }

        project.moveSettings.modify {
            it.suiPath = projectConfig.panelData.suiExec.pathToSettingsFormat()
            it.isValidExec = true
        }
        ProjectManager.getInstance().defaultMoveSettings?.modify {
            it.suiPath = projectConfig.panelData.suiExec.pathToSettingsFormat()
            it.isValidExec = true
        }
        project.addDefaultBuildRunConfiguration(isSelected = true)
        project.openFile(manifestFile)

        updateAllNotifications(project)
        project.moveProjects.refreshAllProjects()
    }

    override fun createStep(
        projectGenerator: DirectoryProjectGenerator<SuiProjectConfig>,
        callback: AbstractNewProjectStep.AbstractCallback<SuiProjectConfig>
    ): AbstractActionWithPanel {
        val suiProjectConfigStep = SuiProjectConfigStep(projectGenerator)
        suiProjectConfigStep.actionButton.addActionListener {

        }
        return suiProjectConfigStep

    }


}
