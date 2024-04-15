package org.sui.cli.settings.aptos

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.*
import org.sui.cli.settings.VersionLabel
import org.sui.openapiext.pathField
import org.sui.stdext.toPathOrNull

class ChooseAptosCliPanel(versionUpdateListener: (() -> Unit)?) : Disposable {

    private val localPathField =
        pathField(
            FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor(),
            this,
            "Choose Aptos CLI",
            onTextChanged = { text ->
                val exec = AptosExec.LocalPath(text)
                _aptosExec = exec
                exec.toPathOrNull()?.let { versionLabel.updateAndNotifyListeners(it) }
            })

    private val versionLabel = VersionLabel(this, versionUpdateListener)

    private lateinit var _aptosExec: AptosExec

    var selectedAptosExec: AptosExec
        get() = _aptosExec
        set(aptosExec) {
            this._aptosExec = aptosExec
            when (_aptosExec) {
                is AptosExec.Bundled -> localPathField.text = ""
                else ->
                    localPathField.text = aptosExec.execPath
            }
            aptosExec.toPathOrNull()?.let { versionLabel.updateAndNotifyListeners(it) }
        }

    fun attachToLayout(layout: Panel): Row {
        val panel = this
        if (!panel::_aptosExec.isInitialized) {
            panel._aptosExec = AptosExec.default()
        }
        val resultRow = with(layout) {
            group("Aptos CLI") {
                buttonsGroup {
                    row {
                        radioButton("Bundled", AptosExec.Bundled)
                            .bindSelected(
                                { _aptosExec is AptosExec.Bundled },
                                {
                                    _aptosExec = AptosExec.Bundled
                                    val bundledPath = AptosExec.Bundled.toPathOrNull()
                                    if (bundledPath != null) {
                                        versionLabel.updateAndNotifyListeners(bundledPath)
                                    }
                                }
                            )
//                            .actionListener { _, _ ->
//                                _aptosExec = AptosExec.Bundled
//                                AptosExec.Bundled.toPathOrNull()
//                                    ?.let { versionLabel.updateValueWithListener(it) }
//                            }
                            .enabled(AptosExec.isBundledSupportedForThePlatform())
                        comment(
                            "Bundled version is not available for this platform (refer to the official Aptos docs for more)"
                        )
                            .visible(!AptosExec.isBundledSupportedForThePlatform())
                    }
                    row {
                        val button = radioButton("Local", AptosExec.LocalPath(""))
                            .bindSelected(
                                { _aptosExec is AptosExec.LocalPath },
                                {
                                    _aptosExec = AptosExec.LocalPath(localPathField.text)
                                    val localPath = localPathField.text.toPathOrNull()
                                    if (localPath != null) {
                                        versionLabel.updateAndNotifyListeners(localPath)
                                    }
                                }
                            )
//                            .actionListener { _, _ ->
//                                _aptosExec = AptosExec.LocalPath(localPathField.text)
//                                localPathField.text.toPathOrNull()
//                                    ?.let { versionLabel.updateAndNotifyListeners(it) }
//                            }
                        cell(localPathField)
                            .enabledIf(button.selected)
                            .align(AlignX.FILL).resizableColumn()
                    }
                    row("--version :") { cell(versionLabel) }
                }
                    .bind(
                        { _aptosExec },
                        {
                            _aptosExec =
                                when (it) {
                                    is AptosExec.Bundled -> it
                                    is AptosExec.LocalPath -> AptosExec.LocalPath(localPathField.text)
                                }
                        }
                    )
            }
        }
        _aptosExec.toPathOrNull()?.let { versionLabel.updateAndNotifyListeners(it) }
        return resultRow
    }

    override fun dispose() {
        Disposer.dispose(localPathField)
    }
}
