package org.sui.ide.utils.imports

import org.sui.ide.inspections.imports.ImportContext
import org.sui.lang.MoveFile
import org.sui.lang.core.psi.MvNamedElement
import org.sui.lang.core.psi.MvQualNamedElement
import org.sui.lang.core.resolve.ContextScopeInfo
import org.sui.lang.core.resolve.MatchingProcessor
import org.sui.lang.core.resolve.processModuleInnerItems
import org.sui.lang.core.resolve.processQualItem
import org.sui.lang.core.resolve.ref.Namespace
import org.sui.lang.core.resolve.ref.Visibility
import org.sui.lang.core.types.ItemQualName
import org.sui.lang.index.MvNamedElementIndex
import org.sui.lang.moveProject
import org.sui.openapiext.common.checkUnitTestMode
import org.sui.openapiext.common.isUnitTestMode

data class ImportCandidate(val element: MvQualNamedElement, val qualName: ItemQualName)

object ImportCandidateCollector {
    fun getImportCandidates(
        context: ImportContext,
        targetName: String,
        itemFilter: (MvQualNamedElement) -> Boolean = { true }
    ): List<ImportCandidate> {
        val (contextElement, namespaces, visibilities, itemVis) = context

        val project = contextElement.project
        val moveProject = contextElement.moveProject ?: return emptyList()
        val searchScope = moveProject.searchScope()

        val allItems = mutableListOf<MvQualNamedElement>()
        if (isUnitTestMode) {
            // always add current file in tests
            val currentFile = contextElement.containingFile as? MoveFile ?: return emptyList()

            val items = mutableListOf<MvQualNamedElement>()
            processFileItemsForUnitTests(currentFile, namespaces, visibilities, itemVis) {
                if (it.element is MvQualNamedElement && it.name == targetName) {
                    items.add(it.element)
                }
                false
            }
//            return elements

//            val items = currentFile.qualifiedItems(targetName, namespaces, visibilities, itemVis)
            allItems.addAll(items)
        }

        MvNamedElementIndex
            .processElementsByName(project, targetName, searchScope) { element ->
                processQualItem(element, namespaces, visibilities, itemVis) {
                    val entryElement = it.element
                    if (entryElement !is MvQualNamedElement) return@processQualItem false
                    if (it.name == targetName) {
                        allItems.add(entryElement)
                    }
                    false
                }
                true
            }

        return allItems
            .filter(itemFilter)
            .mapNotNull { item -> item.qualName?.let { ImportCandidate(item, it) } }
    }
}

private fun processFileItemsForUnitTests(
    file: MoveFile,
    namespaces: Set<Namespace>,
    visibilities: Set<Visibility>,
    contextScopeInfo: ContextScopeInfo,
    processor: MatchingProcessor<MvNamedElement>,
): Boolean {
    checkUnitTestMode()
    for (module in file.modules()) {
        if (
            Namespace.MODULE in namespaces
            && processor.match(contextScopeInfo, module)
        ) {
            return true
        }
        if (processModuleInnerItems(module, namespaces, visibilities, contextScopeInfo, processor)) return true
    }
    return false
}
