package org.sui.lang.core.completion.providers

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.sui.lang.core.MvPsiPatterns.bindingPat
import org.sui.lang.core.completion.createCompletionLookupElement
import org.sui.lang.core.psi.*
import org.sui.lang.core.psi.ext.*
import org.sui.lang.core.resolve.ItemVis
import org.sui.lang.core.resolve.mslLetScope
import org.sui.lang.core.resolve.processItems
import org.sui.lang.core.resolve.ref.Namespace
import org.sui.lang.core.resolve.ref.Visibility
import org.sui.lang.core.withParent
import org.sui.lang.core.withSuperParent

object StructFieldsCompletionProvider : MvCompletionProvider() {
    override val elementPattern: ElementPattern<out PsiElement>
        get() = StandardPatterns.or(
            PlatformPatterns
                .psiElement()
                .withParent<MvStructLitField>(),
            PlatformPatterns
                .psiElement()
                .withParent<MvStructPatField>(),
            bindingPat()
                .withSuperParent<MvStructPatField>(2),
            PlatformPatterns
                .psiElement()
                .withParent<MvStructDotField>(),
        )

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        val pos = parameters.position
        var element = pos.parent
        if (element is MvBindingPat) element = element.parent

        when (element) {
            is MvStructPatField -> {
                val structPat = element.structPat
                addFieldsToCompletion(
                    structPat.path.maybeStruct ?: return,
                    structPat.patFieldNames,
                    result
                )
            }
            is MvStructLitField -> {
                val structLit = element.structLitExpr
                addFieldsToCompletion(
                    structLit.path.maybeStruct ?: return,
                    structLit.fieldNames,
                    result
                )
            }
            is MvStructDotField -> {
                val itemVis = ItemVis(
                    namespaces = setOf(Namespace.DOT_FIELD),
                    visibilities = Visibility.none(),
                    mslLetScope = element.mslLetScope,
                    itemScope = element.itemScope,
                )
                processItems(element, itemVis) {
                    val field = it.element as? MvStructField
                    if (field != null) {
                        result.addElement(field.createCompletionLookupElement())
                    }
                    false
                }
            }
        }
    }

    private fun addFieldsToCompletion(
        referredStruct: MvStruct,
        providedFieldNames: List<String>,
        result: CompletionResultSet,
    ) {
        for (field in referredStruct.fields.filter { it.name !in providedFieldNames }) {
            result.addElement(field.createCompletionLookupElement())
        }
    }
}
