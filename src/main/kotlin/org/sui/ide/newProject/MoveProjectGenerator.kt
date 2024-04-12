package org.sui.ide.newProject

import com.intellij.ide.util.projectWizard.AbstractNewProjectStep
import com.intellij.ide.util.projectWizard.CustomStepProjectGenerator
import com.intellij.ide.util.projectWizard.ProjectSettingsStepBase
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.welcomeScreen.AbstractActionWithPanel
import com.intellij.platform.DirectoryProjectGenerator
import com.intellij.platform.DirectoryProjectGeneratorBase
import com.intellij.platform.ProjectGeneratorPeer
import org.sui.cli.PluginApplicationDisposable
import org.sui.cli.runConfigurations.InitProjectCli
import org.sui.cli.settings.Blockchain
import org.sui.cli.settings.moveSettings
import org.sui.ide.MoveIcons
import org.sui.openapiext.computeWithCancelableProgress
import org.sui.stdext.unwrapOrThrow

data class MoveProjectConfig(val blockchain: Blockchain, val initCli: InitProjectCli)

class MoveProjectGenerator : DirectoryProjectGeneratorBase<MoveProjectConfig>(),
    CustomStepProjectGenerator<MoveProjectConfig> {

    private val disposable = service<PluginApplicationDisposable>()

    override fun getName() = "Sui Move"
    override fun getLogo() = MoveIcons.MOVE_LOGO
    override fun createPeer(): ProjectGeneratorPeer<MoveProjectConfig> = MoveProjectGeneratorPeer(disposable)

    override fun generateProject(
        project: Project,
        baseDir: VirtualFile,
        projectConfig: MoveProjectConfig,
        module: Module
    ) {
        val packageName = project.name
        val blockchain = projectConfig.blockchain
        val projectCli = projectConfig.initCli
        val manifestFile =
            project.computeWithCancelableProgress("Generating $blockchain project...") {
                val manifestFile =
                    projectCli.init(
                        project,
                        disposable,
                        rootDirectory = baseDir,
                        packageName = packageName
                    )
                        .unwrapOrThrow() // TODO throw? really??
                manifestFile
            }
        // update settings (and refresh Aptos projects too)
        project.moveSettings.modify {
            it.blockchain = blockchain
            when (projectCli) {
                is InitProjectCli.Aptos -> {
                    it.aptosPath = projectCli.aptosExec.pathToSettingsFormat()
                }

                is InitProjectCli.Sui -> {
                    it.suiPath = projectCli.suiExec.pathToSettingsFormat()
                }
            }
        }
        ProjectInitializationSteps.createDefaultCompileConfigurationIfNotExists(project)
        ProjectInitializationSteps.openMoveTomlInEditor(project, manifestFile)
    }

    override fun createStep(
        projectGenerator: DirectoryProjectGenerator<MoveProjectConfig>,
        callback: AbstractNewProjectStep.AbstractCallback<MoveProjectConfig>
    ): AbstractActionWithPanel =
        ConfigStep(projectGenerator)

    class ConfigStep(generator: DirectoryProjectGenerator<MoveProjectConfig>) :
        ProjectSettingsStepBase<MoveProjectConfig>(
            generator,
            AbstractNewProjectStep.AbstractCallback()
        )

}
