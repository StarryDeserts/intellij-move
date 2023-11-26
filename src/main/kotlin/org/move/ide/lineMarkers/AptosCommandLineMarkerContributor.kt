package org.move.ide.lineMarkers

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.psi.PsiElement
import org.move.cli.runConfigurations.sui.run.RunCommandConfigurationHandler
import org.move.cli.runConfigurations.sui.view.ViewCommandConfigurationHandler
import org.move.cli.runConfigurations.producers.TestCommandConfigurationProducer
import org.move.ide.MoveIcons
import org.move.lang.MvElementTypes.IDENTIFIER
import org.move.lang.core.psi.MvFunction
import org.move.lang.core.psi.MvModule
import org.move.lang.core.psi.MvNameIdentifierOwner
import org.move.lang.core.psi.ext.elementType
import org.move.lang.core.psi.ext.isEntry
import org.move.lang.core.psi.ext.isTest
import org.move.lang.core.psi.ext.isView

class AptosCommandLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        if (element.elementType != IDENTIFIER) return null

        val parent = element.parent
        if (parent !is MvNameIdentifierOwner || element != parent.nameIdentifier) return null

        if (parent is MvFunction) {
            when {
                parent.isTest -> {
                    val config =
                        TestCommandConfigurationProducer.fromLocation(parent, climbUp = false)
                    if (config != null) {
                        return Info(
                            MoveIcons.RUN_TEST_ITEM,
                            { config.configurationName },
                            *contextActions()
                        )
                    }
                }
                parent.isEntry -> {
                    val config = RunCommandConfigurationHandler().configurationFromLocation(parent)
                    if (config != null) {
                        return Info(
                            MoveIcons.RUN_TRANSACTION_ITEM,
                            { config.configurationName },
                            *contextActions()
                        )
                    }
                }
                parent.isView -> {
                    val config = ViewCommandConfigurationHandler().configurationFromLocation(parent)
                    if (config != null) {
                        return Info(
                            MoveIcons.VIEW_FUNCTION_ITEM,
                            { config.configurationName },
                            *contextActions()
                        )
                    }
                }
            }
        }
        if (parent is MvModule) {
            val testConfig = TestCommandConfigurationProducer.fromLocation(parent, climbUp = false)
            if (testConfig != null) {
                return Info(
                    MoveIcons.RUN_ALL_TESTS_IN_ITEM,
                    { testConfig.configurationName },
                    *contextActions()
                )
            }
        }
        return null
    }
}

private fun contextActions(): Array<AnAction> {
    return ExecutorAction.getActions(0).toList()
//        .filter { it.toString().startsWith("Run context configuration") }
        .toTypedArray()
}
