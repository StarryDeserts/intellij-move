package org.move.cli.runConfigurations.producers

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import org.move.cli.MoveProject
import org.move.cli.runConfigurations.sui.SuiCommandLine
import org.move.cli.runConfigurations.sui.SuiConfigurationType
import org.move.cli.runConfigurations.sui.any.AnyCommandConfigurationFactory
import org.move.cli.settings.skipFetchLatestGitDeps
import org.move.lang.MoveFile
import org.move.lang.core.psi.MvFunction
import org.move.lang.core.psi.MvModule
import org.move.lang.core.psi.containingModule
import org.move.lang.core.psi.ext.findMoveProject
import org.move.lang.core.psi.ext.hasTestFunctions
import org.move.lang.core.psi.ext.isTest
import org.move.lang.moveProject
import org.toml.lang.psi.TomlFile

class AnyCommandConfigurationProducer : CommandConfigurationProducerBase() {

    override fun getConfigurationFactory() =
        AnyCommandConfigurationFactory(SuiConfigurationType.getInstance())

    override fun configFromLocation(location: PsiElement) = fromLocation(location)

    companion object {
        fun fromLocation(location: PsiElement, climbUp: Boolean = true): CommandLineFromContext? {
            return when {
                location is MoveFile -> {
                    val module = location.modules().firstOrNull { it.hasTestFunctions() } ?: return null
                    findTestModule(module, climbUp)
                }
                location is TomlFile && location.name == "Move.toml" -> {
                    val moveProject = location.findMoveProject() ?: return null
                    findTestProject(location, moveProject)
                }
                location is PsiDirectory -> {
                    val moveProject = location.findMoveProject() ?: return null
                    if (
                        location.virtualFile == moveProject.currentPackage.contentRoot
                        || location.virtualFile == moveProject.currentPackage.testsFolder
                    ) {
                        findTestProject(location, moveProject)
                    } else {
                        null
                    }
                }
                else -> findTestFunction(location, climbUp) ?: findTestModule(location, climbUp)
            }
        }

        private fun findTestFunction(psi: PsiElement, climbUp: Boolean): CommandLineFromContext? {
            val fn = findElement<MvFunction>(psi, climbUp) ?: return null
            if (!fn.isTest) return null

            val modName = fn.containingModule?.name ?: return null
            val functionName = fn.name ?: return null

            val confName = "Test $modName::$functionName"
            var subCommand = "move test --filter $modName::$functionName"
            if (psi.project.skipFetchLatestGitDeps) {
                subCommand += " --skip-fetch-latest-git-deps"
            }

            val moveProject = fn.moveProject ?: return null
            val rootPath = moveProject.contentRootPath ?: return null
            return CommandLineFromContext(
                fn,
                confName,
                SuiCommandLine(subCommand, workingDirectory = rootPath)
            )
        }

        private fun findTestModule(psi: PsiElement, climbUp: Boolean): CommandLineFromContext? {
            val mod = findElement<MvModule>(psi, climbUp) ?: return null
            if (!mod.hasTestFunctions()) return null

            val modName = mod.name ?: return null
            val confName = "Test $modName"

            var subCommand = "move test --filter $modName"
            if (psi.project.skipFetchLatestGitDeps) {
                subCommand += " --skip-fetch-latest-git-deps"
            }

            val moveProject = mod.moveProject ?: return null
            val rootPath = moveProject.contentRootPath ?: return null
            return CommandLineFromContext(
                mod,
                confName,
                SuiCommandLine(subCommand, workingDirectory = rootPath)
            )
        }

        private fun findTestProject(
            location: PsiFileSystemItem,
            moveProject: MoveProject
        ): CommandLineFromContext? {
            val packageName = moveProject.currentPackage.packageName
            val rootPath = moveProject.contentRootPath ?: return null

            val confName = "Test $packageName"
            var subCommand = "move test"
            if (location.project.skipFetchLatestGitDeps) {
                subCommand += " --skip-fetch-latest-git-deps"
            }

            return CommandLineFromContext(
                location,
                confName,
                SuiCommandLine(subCommand, workingDirectory = rootPath)
            )
        }
    }
}
