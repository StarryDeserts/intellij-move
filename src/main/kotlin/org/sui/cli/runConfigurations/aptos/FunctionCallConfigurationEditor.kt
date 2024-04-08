package org.sui.cli.runConfigurations.aptos

import com.intellij.icons.AllIcons
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.*
import com.intellij.xdebugger.impl.ui.TextViewer
import org.sui.cli.MoveProject
import org.sui.cli.moveProjectsService
import org.sui.stdext.RsResult
import org.sui.utils.ui.whenItemSelectedFromUi
import org.sui.utils.ui.whenTextChangedFromUi
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTextField

data class MoveProjectItem(val moveProject: MoveProject) {
    override fun toString(): String {
        return "${moveProject.currentPackage.packageName} [${moveProject.contentRootPath}]"
    }
}

class FunctionCallConfigurationEditor<T : FunctionCallConfigurationBase>(
    private val commandHandler: CommandConfigurationHandler,
    private var moveProject: MoveProject,
) :
    SettingsEditor<T>() {

    private val project = moveProject.project

    private var signerAccount: String? = null
    private var functionCall: FunctionCall? = null

    private val projectComboBox: ComboBox<MoveProjectItem> = ComboBox()
    private val accountTextField = JTextField()
    private val rawCommandField = TextViewer("", project, true)

    private val functionParametersPanel = FunctionParametersPanel(commandHandler, moveProject)

    private val errorLabel = JLabel("")

    private lateinit var editorPanel: DialogPanel

    init {
        errorLabel.foreground = JBColor.RED

        moveProject.project.moveProjectsService.allProjects.forEach {
            projectComboBox.addItem(MoveProjectItem(it))
        }
        projectComboBox.isEnabled = projectComboBox.model.size > 1
        projectComboBox.selectedItem = MoveProjectItem(moveProject)

        val editor = this
        functionParametersPanel.addFunctionCallListener(object : FunctionParameterPanelListener {
            override fun functionParametersChanged(functionCall: FunctionCall) {
                editor.functionCall = functionCall
                editor.rawCommandField.text =
                    commandHandler.generateCommand(moveProject, functionCall, signerAccount).unwrapOrNull() ?: ""
            }
        })
        functionParametersPanel.setMoveProjectAndCompletionVariants(moveProject)
    }

    override fun resetEditorFrom(s: T) {
        val moveProject = s.workingDirectory?.let { project.moveProjectsService.findMoveProjectForPath(it) }
        if (moveProject == null) {
            setErrorText("Deserialization error: no Aptos project found in the specified working directory")
            editorPanel.isVisible = false
            this.signerAccount = null
            this.functionCall = null
            return
        }

        val res = commandHandler.parseCommand(moveProject, s.command)
        val (profile, functionCall) = when (res) {
            is RsResult.Ok -> res.ok
            is RsResult.Err -> {
                setErrorText("Deserialization error: ${res.err}")
                editorPanel.isVisible = false
                signerAccount = null
                functionCall = null
                return
            }
        }
        this.signerAccount = profile
        this.accountTextField.text = profile

        functionParametersPanel.updateFromFunctionCall(functionCall)
    }

    override fun applyEditorTo(s: T) {
        functionParametersPanel.fireChangeEvent()
        s.command = rawCommandField.text
        s.moveProjectFromWorkingDirectory = moveProject
    }

    override fun disposeEditor() {
        Disposer.dispose(functionParametersPanel)
    }

    override fun createEditor(): JComponent {
        editorPanel = createEditorPanel()
        val outerPanel = panel {
            row { cell(errorLabel) }
            row {
                cell(editorPanel)
                    .align(AlignX.FILL + AlignY.FILL)
            }
        }
        return DumbService.getInstance(project).wrapGently(outerPanel, this)
    }

    private fun createEditorPanel(): DialogPanel {
        val editorPanel = panel {
            row { cell(errorLabel) }
            row("Project") {
                cell(projectComboBox)
                    .align(AlignX.FILL)
                    .columns(COLUMNS_LARGE)
                    .whenItemSelectedFromUi {
                        moveProject = it.moveProject
                        functionParametersPanel.setMoveProjectAndCompletionVariants(moveProject)
                    }
            }
            row("Account") {
                cell(accountTextField)
                    .align(AlignX.FILL)
                    .whenTextChangedFromUi {
                        signerAccount = it
                    }
            }
            separator()
            row {
                cell(functionParametersPanel)
                    .align(AlignX.FILL + AlignY.FILL)
            }
            separator()
            row("Raw") {
                cell(rawCommandField)
                    .align(AlignX.FILL)
            }
        }
        editorPanel.registerValidators(this)
        return editorPanel
    }

//    private fun validateEditor() {
//        val functionCall = this.functionCall
//        if (functionCall == null) {
//            setErrorText("FunctionId is required")
//            return
//        }
//        val functionItemName = functionCall.itemName()
//        if (functionItemName == null) {
//            setErrorText("FunctionId is required")
//            return
//        }
//        val function = handler.getFunction(moveProject, functionItemName)
//        if (function == null) {
//            setErrorText("Cannot resolve function from functionId")
//            return
//        }
//        val typeParams = functionCall.typeParams.filterValues { it == null }
//        if (typeParams.isNotEmpty()) {
//            setErrorText("Missing required type parameters: ${typeParams.keys.joinToString()}")
//            return
//        }
//        val valueParams = functionCall.valueParams.filterValues { it == null }
//        if (valueParams.isNotEmpty()) {
//            setErrorText("Missing required value parameters: ${valueParams.keys.joinToString()}")
//            return
//        }
//        setErrorText("")
//    }

    private fun setErrorText(text: String) {
        errorLabel.text = text
        errorLabel.foreground = MessageType.ERROR.titleForeground
        errorLabel.icon = if (text.isBlank()) null else AllIcons.Actions.Lightning
    }
}
