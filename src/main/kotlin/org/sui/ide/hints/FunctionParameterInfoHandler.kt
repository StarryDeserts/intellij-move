package org.sui.ide.hints

import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.ParameterInfoUtils
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.sui.ide.utils.FunctionSignature
import org.sui.lang.MvElementTypes
import org.sui.lang.core.psi.MvCallExpr
import org.sui.lang.core.psi.MvStructLitFieldsBlock
import org.sui.lang.core.psi.MvValueArgumentList
import org.sui.lang.core.psi.ext.ancestorOrSelf
import org.sui.lang.core.psi.ext.startOffset
import org.sui.utils.AsyncParameterInfoHandlerBase

class FunctionParameterInfoHandler : AsyncParameterInfoHandlerBase<MvValueArgumentList, ParamsDescription>() {

    override fun findTargetElement(file: PsiFile, offset: Int): MvValueArgumentList? {
        val element = file.findElementAt(offset) ?: return null
//        val callExpr = element.ancestorStrict<MvCallArgumentList>() ?: return null
//        val block = element.ancestorStrict<MvStructLitFieldsBlock>()
//        if (block != null && callExpr.contains(block)) return null
        return element.ancestorOrSelf(stopAt = MvStructLitFieldsBlock::class.java)
//        return callExpr
    }

    override fun calculateParameterInfo(element: MvValueArgumentList): Array<ParamsDescription>? =
        ParamsDescription.findDescription(element)?.let { arrayOf(it) }

    override fun updateParameterInfo(parameterOwner: MvValueArgumentList, context: UpdateParameterInfoContext) {
        if (context.parameterOwner != parameterOwner) {
            context.removeHint()
            return
        }
        val contextOffset = context.offset
        val currentParameterIndex =
            if (parameterOwner.startOffset == contextOffset) {
                -1
            } else {
                if (parameterOwner.valueArgumentList.isEmpty()) {
                    0
                } else {
                    ParameterInfoUtils.getCurrentParameterIndex(
                        parameterOwner.node,
                        context.offset,
                        MvElementTypes.COMMA
                    )
                }
            }
        context.setCurrentParameter(currentParameterIndex)
    }

    override fun updateUI(description: ParamsDescription, context: ParameterInfoUIContext) {
        val range = description.getArgumentRange(context.currentParameterIndex)
        context.setupUIComponentPresentation(
            description.presentText,
            range.startOffset,
            range.endOffset,
            !context.isUIComponentEnabled,
            false,
            false,
            context.defaultParameterColor
        )
    }

}

class ParamsDescription(val parameters: Array<String>) {
    fun getArgumentRange(index: Int): TextRange {
        if (index < 0 || index >= parameters.size) return TextRange.EMPTY_RANGE
        val start = parameters.take(index).sumOf { it.length + 2 }
        return TextRange(start, start + parameters[index].length)
    }

    val presentText = if (parameters.isEmpty()) "<no arguments>" else parameters.joinToString(", ")

    companion object {
        /**
         * Finds declaration of the func/method and creates description of its arguments
         */
        fun findDescription(args: MvValueArgumentList): ParamsDescription? {
            val signature =
                (args.parent as? MvCallExpr)?.let { FunctionSignature.resolve(it) } ?: return null
            val params = signature.parameters.map { "${it.name}: ${it.type}" }
            return ParamsDescription(params.toTypedArray())
        }
    }
}
