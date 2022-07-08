package org.move.cli

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.isDirectory
import org.move.cli.settings.isValidExecutable
import org.move.openapiext.*
import org.move.openapiext.common.isUnitTestMode
import org.move.stdext.MvResult
import org.move.stdext.isExecutableFile
import org.move.stdext.toPathOrNull
import org.move.stdext.unwrapOrElse
import java.io.File
import java.nio.file.Path

class Aptos(private val aptosPath: Path) {

    fun init(
        project: Project,
        owner: Disposable,
        privateKeyPath: String,
        faucetUrl: String,
        restUrl: String,
    ): MvProcessResult<ProcessOutput> {
        if (!isUnitTestMode) {
            checkIsBackgroundThread()
        }
        val commandLine = GeneralCommandLine(aptosPath.toString(), "init")
            .withWorkDirectory(project.root)
            .withParameters(
                listOf(
                    "--private-key-file", privateKeyPath,
                    "--faucet-url", faucetUrl,
                    "--rest-url", restUrl,
                    "--assume-yes"
                )
            )
            .withEnvironment(emptyMap())
            .withCharset(Charsets.UTF_8)
        return commandLine.execute(owner)
    }

    @Suppress("FunctionName")
    fun move_init(
        project: Project,
        owner: Disposable,
        rootDirectory: VirtualFile,
        packageName: String,
    ): MvProcessResult<VirtualFile> {
        if (!isUnitTestMode) {
            checkIsBackgroundThread()
        }
        val commandLine = GeneralCommandLine(aptosPath.toString(), "move", "init")
            .withWorkDirectory(project.root)
            .withParameters(
                listOf(
                    "--name", packageName,
                    "--assume-yes"
                )
            )
            .withEnvironment(emptyMap())
            .withCharset(Charsets.UTF_8)
        commandLine.execute(owner).unwrapOrElse { return MvResult.Err(it) }
        fullyRefreshDirectory(rootDirectory)

        val manifest =
            checkNotNull(rootDirectory.findChild(Consts.MANIFEST_FILE)) { "Can't find the manifest file" }
        return MvResult.Ok(manifest)
    }

    fun version(workingDirectory: Path? = null): String? {
        if (!isUnitTestMode) {
            checkIsBackgroundThread()
        }
        if (!aptosPath.isValidExecutable()) return null

        val commandLine = GeneralCommandLine(aptosPath.toString())
            .withWorkDirectory(workingDirectory)
            .withParameters(listOf("--version"))
            .withEnvironment(emptyMap())
            .withCharset(Charsets.UTF_8)
        val lines = commandLine.execute()?.stdoutLines.orEmpty()
        return if (lines.isNotEmpty()) return lines.joinToString("\n") else null
    }

    companion object {
        data class GeneratedFilesHolder(val manifest: VirtualFile)

        fun suggestPath(): String? {
            for (path in homePathCandidates()) {
                when {
                    path.isDirectory() -> {
                        val candidate = path.resolveExisting(executableName("aptos")) ?: continue
                        if (candidate.isExecutableFile())
                            return candidate.toAbsolutePath().toString()
                    }
                    path.isExecutableFile() && path.fileName.toString() == executableName("aptos") -> {
                        if (path.isExecutableFile())
                            return path.toAbsolutePath().toString()
                    }
                }
            }
            return null
        }

        private fun homePathCandidates(): Sequence<Path> {
            return System.getenv("PATH")
                .orEmpty()
                .split(File.pathSeparator)
                .asSequence()
                .filter { it.isNotEmpty() }
                .mapNotNull { it.toPathOrNull() }
        }

        private fun executableName(toolName: String): String =
            if (SystemInfo.isWindows) "$toolName.exe" else toolName

    }
}