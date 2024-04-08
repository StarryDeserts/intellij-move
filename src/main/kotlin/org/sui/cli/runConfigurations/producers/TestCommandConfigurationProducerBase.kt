package org.sui.cli.runConfigurations.producers

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import org.sui.cli.MoveProject
import org.sui.cli.runConfigurations.CliCommandLineArgs
import org.sui.cli.settings.Blockchain
import org.sui.cli.settings.dumpStateOnTestFailure
import org.sui.cli.settings.skipFetchLatestGitDeps
import org.sui.lang.MoveFile
import org.sui.lang.core.psi.MvFunction
import org.sui.lang.core.psi.MvModule
import org.sui.lang.core.psi.containingModule
import org.sui.lang.core.psi.ext.findMoveProject
import org.sui.lang.core.psi.ext.hasTestAttr
import org.sui.lang.core.psi.ext.hasTestFunctions
import org.sui.lang.moveProject
import org.toml.lang.psi.TomlFile

abstract class TestCommandConfigurationProducerBase(blockchain: Blockchain) :
    CommandConfigurationProducerBase(blockchain) {

    override fun fromLocation(location: PsiElement, climbUp: Boolean): CommandLineArgsFromContext? {
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

    private fun findTestFunction(psi: PsiElement, climbUp: Boolean): CommandLineArgsFromContext? {
        val fn = findElement<MvFunction>(psi, climbUp) ?: return null
        if (!fn.hasTestAttr) return null

        val modName = fn.containingModule?.name ?: return null
        val functionName = fn.name ?: return null

        val confName = "Test $modName::$functionName"
        var subCommand = "move test"
        when (blockchain) {
            Blockchain.APTOS -> subCommand += " --filter $modName::$functionName"
            Blockchain.SUI -> subCommand += " $modName::$functionName"
        }
        if (psi.project.skipFetchLatestGitDeps) {
            subCommand += " --skip-fetch-latest-git-deps"
        }
        if (blockchain == Blockchain.APTOS && psi.project.dumpStateOnTestFailure) {
            subCommand += " --dump"
        }

        val moveProject = fn.moveProject ?: return null
        val rootPath = moveProject.contentRootPath ?: return null
        return CommandLineArgsFromContext(
            fn,
            confName,
            CliCommandLineArgs(subCommand, workingDirectory = rootPath)
        )
    }

    private fun findTestModule(psi: PsiElement, climbUp: Boolean): CommandLineArgsFromContext? {
        val mod = findElement<MvModule>(psi, climbUp) ?: return null
        if (!mod.hasTestFunctions()) return null

        val modName = mod.name ?: return null
        val confName = "Test $modName"

        var subCommand = "move test"
        when (blockchain) {
            Blockchain.APTOS -> subCommand += " --filter $modName"
            Blockchain.SUI -> subCommand += " $modName"
        }
        if (psi.project.skipFetchLatestGitDeps) {
            subCommand += " --skip-fetch-latest-git-deps"
        }
        if (blockchain == Blockchain.APTOS && psi.project.dumpStateOnTestFailure) {
            subCommand += " --dump"
        }

        val moveProject = mod.moveProject ?: return null
        val rootPath = moveProject.contentRootPath ?: return null
        return CommandLineArgsFromContext(
            mod,
            confName,
            CliCommandLineArgs(subCommand, workingDirectory = rootPath)
        )
    }

    private fun findTestProject(
        location: PsiFileSystemItem,
        moveProject: MoveProject
    ): CommandLineArgsFromContext? {
        val packageName = moveProject.currentPackage.packageName
        val rootPath = moveProject.contentRootPath ?: return null

        val confName = "Test $packageName"
        var subCommand = "move test"
        if (location.project.skipFetchLatestGitDeps) {
            subCommand += " --skip-fetch-latest-git-deps"
        }
        if (location.project.dumpStateOnTestFailure) {
            subCommand += " --dump"
        }

        return CommandLineArgsFromContext(
            location,
            confName,
            CliCommandLineArgs(subCommand, workingDirectory = rootPath)
        )
    }
}