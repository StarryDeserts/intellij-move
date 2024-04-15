package org.sui.cli.runConfigurations.sui

import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.SimpleConfigurationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import org.sui.ide.MoveIcons

class SuiConfigurationType :
    SimpleConfigurationType(
        "SuiCommandConfiguration",
        "Sui",
        "Sui command execution",
        NotNullLazyValue.createConstantValue(MoveIcons.SUI_LOGO)
    ) {

    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return SuiCommandConfiguration(project, this)
    }

    companion object {
        fun getInstance(): SuiConfigurationType {
            return ConfigurationTypeUtil.findConfigurationType(SuiConfigurationType::class.java)
        }
    }
}