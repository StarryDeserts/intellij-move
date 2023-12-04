package org.sui.lang.core.completion.providers

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.patterns.ElementPattern
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.sui.ide.inspections.imports.ImportContext
import org.sui.lang.core.MvPsiPatterns
import org.sui.lang.core.completion.*
import org.sui.lang.core.psi.MvPath
import org.sui.lang.core.psi.containingModule
import org.sui.lang.core.psi.containingModuleSpec
import org.sui.lang.core.psi.ext.equalsTo
import org.sui.lang.core.psi.itemScope
import org.sui.lang.core.resolve.ItemVis
import org.sui.lang.core.resolve.mslLetScope
import org.sui.lang.core.resolve.processItems
import org.sui.lang.core.resolve.ref.Namespace
import org.sui.lang.core.resolve.ref.Visibility

object ModulesCompletionProvider : MvCompletionProvider() {
    override val elementPattern: ElementPattern<PsiElement>
        get() =
            MvPsiPatterns.path()

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        val maybePath = parameters.position.parent
        val refElement =
            maybePath as? MvPath ?: maybePath.parent as MvPath

        if (parameters.position !== refElement.referenceNameElement) return
        if (refElement.moduleRef != null) return

        val processedNames = mutableSetOf<String>()
        val itemVis =
            ItemVis(
                setOf(Namespace.MODULE),
                visibilities = Visibility.local(),
                mslLetScope = refElement.mslLetScope,
                itemScope = refElement.itemScope,
            )
        val ctx = CompletionContext(refElement, itemVis)
        processItems(refElement, itemVis) {
            val lookup = it.element.createLookupElement(
                ctx,
                priority = IMPORTED_MODULE_PRIORITY
            )
//            val lookup = it.element.createCompletionLookupElement(
//                priority = IMPORTED_MODULE_PRIORITY,
////                props = props,
//            )
            result.addElement(lookup)
            it.element.name?.let(processedNames::add)
            false
        }

        // disable auto-import in module specs for now
        if (refElement.containingModuleSpec != null) return

        val path = parameters.originalPosition?.parent as? MvPath ?: return
        val importContext =
            ImportContext.from(path, itemVis.copy(visibilities = setOf(Visibility.Public)))
        val containingMod = path.containingModule
        val candidates = getImportCandidates(parameters, result, processedNames, importContext,
                                             itemFilter = {
                                                 containingMod != null && !it.equalsTo(
                                                     containingMod
                                                 )
                                             })
        candidates.forEach { candidate ->
            val lookupElement = candidate.element.createCompletionLookupElement(
                ImportInsertHandler(parameters, candidate),
                importContext.itemVis.namespaces,
                priority = UNIMPORTED_ITEM_PRIORITY,
//                props = props,
            )
            result.addElement(lookupElement)
        }
    }
}
