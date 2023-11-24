package org.move.cli.runConfigurations.aptos

import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.util.NotNullLazyValue
import org.move.cli.runConfigurations.aptos.any.AnyCommandConfigurationFactory
import org.move.cli.runConfigurations.aptos.run.RunCommandConfigurationFactory
import org.move.cli.runConfigurations.aptos.view.ViewCommandConfigurationFactory
import org.move.ide.MoveIcons

class AptosConfigurationType :
    ConfigurationTypeBase(
        "SuiCommandConfiguration",
        "Sui",
        "Sui command execution",
        NotNullLazyValue.createConstantValue(MoveIcons.SUI_ICON)
    ) {

    init {
        addFactory(RunCommandConfigurationFactory(this))
        addFactory(ViewCommandConfigurationFactory(this))
        addFactory(AnyCommandConfigurationFactory(this))
    }

    companion object {
        fun getInstance(): AptosConfigurationType {
            return ConfigurationTypeUtil.findConfigurationType(AptosConfigurationType::class.java)
        }
    }
}
