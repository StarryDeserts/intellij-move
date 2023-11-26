package org.move.cli.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.selected
import org.move.cli.runConfigurations.aptos.AptosCliExecutor
import org.move.cli.runConfigurations.sui.SuiCliExecutor
import org.move.openapiext.UiDebouncer
import org.move.openapiext.pathField
import org.move.openapiext.showSettings
import org.move.stdext.toPathOrNull

import com.intellij.openapi.application.PathManager
import java.io.File
import java.nio.file.Paths

class SuiSettingsPanel(
    private val showDefaultProjectSettingsLink: Boolean,
    private val updateListener: (() -> Unit)? = null
): Disposable {

    private val localPathField =
        pathField(
            FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor(),
            this,
            "Choose Sui CLI"
        ) { text ->
            suiExec = SuiExec.LocalPath(text)
            onSuiExecUpdate()
        }

    private val versionLabel = VersionLabel()
    private val versionUpdateDebouncer = UiDebouncer(this)

    data class PanelData(val suiExec: SuiExec)

    var panelData: PanelData
        get() = PanelData(suiExec)
        set(value) {
            when (value.suiExec) {
                is SuiExec.Bundled -> localPathField.text = value.suiExec.execPath
                else -> localPathField.text = value.suiExec.execPath
            }
            onSuiExecUpdate()
        }

    var suiExec: SuiExec = SuiExec.Bundled

    fun attachTo(layout: Panel) = with(layout) {
        // Don't use `Project.toolchain` or `Project.rustSettings` here because
        // `getService` can return `null` for default project after dynamic plugin loading.
        // As a result, you can get `java.lang.IllegalStateException`
        // So let's handle it manually
        val defaultProjectSettings = ProjectManager.getInstance().defaultProject.getService(MoveProjectSettingsService::class.java)
        panelData = PanelData(
            suiExec = SuiExec.fromSettingsFormat(defaultProjectSettings.state.suiPath),
        )
        val localSuiPath = findSuiCommand()
        localSuiPath?:let {
            suiExec = SuiExec.fromSettingsFormat(localSuiPath)
            println("找到本地sui目录："+findSuiCommand())
        }

        group("Sui CLI") {
//            row {
//                radioButton("Bundled")
//                    .bindSelected(
//                        { suiExec is AptosExec.Bundled },
//                        {
//                            suiExec = AptosExec.Bundled
//                            onSuiExecUpdate()
//                        }
//                    )
//            }
            row {
                label("Local")
//                    .bindSelected(
//                        { suiExec is SuiExec.LocalPath },
//                        {
//                            suiExec = SuiExec.LocalPath(localPathField.text)
//                            onSuiExecUpdate()
//                        }
//                    )
                cell(localPathField)
                    .align(AlignX.FILL).resizableColumn()
            }
        }
        row("sui --version :") { cell(versionLabel) }
        row {
            link("Set default project settings") {
                ProjectManager.getInstance().defaultProject.showSettings<PerProjectMoveConfigurable>()
            }
                .visible(showDefaultProjectSettingsLink)
                .align(AlignX.RIGHT)
//                .horizontalAlign(HorizontalAlign.RIGHT)
        }
    }


    fun findSuiCommand(): String? {
        // Define possible sui command locations for Unix-based systems and Windows
        val possibleUnixPaths = listOf("/usr/local/bin/sui", "/usr/bin/sui", "/bin/sui")
        val windowsPath = listOf(
            System.getenv("ProgramFiles") + "\\Sui\\sui.exe" ,
            System.getProperty("user.home")+"\\sui\\sui.exe"
        )

        // Check Unix-based paths
        possibleUnixPaths.forEach {
            if (File(it).exists()) return Paths.get(it).parent.toString()
        }

        windowsPath.forEach{
            if (File(it).exists()) return Paths.get(it).parent.toString()
        }
        // Check the PATH environment variable
        val systemPath = System.getenv("PATH").split(File.pathSeparator)
        systemPath.forEach { path ->
            val fullPath = Paths.get(path, "sui").toString()
            if (File(fullPath).exists()) return Paths.get(fullPath).parent.toString()
        }

        // If not found, return null or throw an exception
        return null
    }



    private fun onSuiExecUpdate() {
        val suiExecPath = suiExec.execPath.toPathOrNull()
        versionUpdateDebouncer.run(
            onPooledThread = {
                suiExecPath?.let { SuiCliExecutor(it).version() }
            },
            onUiThread = { version ->
                versionLabel.setVersion(version)
                updateListener?.invoke()
            }
        )
    }

    override fun dispose() {
        Disposer.dispose(localPathField)
    }
}
