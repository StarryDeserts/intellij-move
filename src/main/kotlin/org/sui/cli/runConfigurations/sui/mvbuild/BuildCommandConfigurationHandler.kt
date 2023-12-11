package org.sui.cli.runConfigurations.sui.run

import org.sui.cli.MoveProject
import org.sui.cli.runConfigurations.sui.CommandConfigurationHandler
import org.sui.lang.core.psi.MvFunction
import org.sui.lang.core.psi.MvFunctionParameter
import org.sui.lang.core.psi.ext.isEntry
import org.sui.lang.core.psi.ext.isTest
import org.sui.lang.core.psi.ext.transactionParameters
import org.sui.lang.index.MvEntryFunctionIndex

class BuildCommandConfigurationHandler : CommandConfigurationHandler() {

    override val subCommand: String get() = "move build"

    override fun configurationName(functionId: String): String = "Build $functionId"

    override fun functionPredicate(function: MvFunction): Boolean = function.isEntry && !function.isTest

    override fun getFunction(moveProject: MoveProject, functionQualName: String): MvFunction? {
        return getEntryFunction(moveProject, functionQualName)
    }

    override fun getFunctionByCmdName(moveProject: MoveProject, functionCmdName: String): MvFunction? {
        return getEntryFunction(moveProject, functionCmdName)
    }

    override fun getFunctionParameters(function: MvFunction): List<MvFunctionParameter> {
        return function.transactionParameters
    }

    override fun getFunctionCompletionVariants(moveProject: MoveProject): Collection<String> {
//        println("fetch completion variants")
        return MvEntryFunctionIndex.getAllKeys(moveProject.project)
//        val completionVariants = mutableListOf<String>()
//        for (key in keys) {
//            val functions =
//                StubIndex.getElements(MvEntryFunctionIndex.KEY, key, moveProject.project, null, MvFunction::class.java)
//            for (function in functions) {
//                if (function.moveProject != moveProject) continue
//                val qualName = function.qualName?.editorText() ?: continue
//                completionVariants.add(qualName)
//            }
//        }
//        return completionVariants
    }

    companion object {
        fun getEntryFunction(moveProject: MoveProject, functionId: String): MvFunction? {
            return MvEntryFunctionIndex.getFunctionByFunctionId(
                moveProject,
                functionId,
                itemFilter = { fn -> fn.isEntry }
            )
        }
    }
}
