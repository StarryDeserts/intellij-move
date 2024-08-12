package org.sui.ide.utils.imports

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.psi.PsiElement
import org.sui.ide.inspections.imports.ImportContext
import org.sui.lang.core.psi.MvQualNamedElement
import org.sui.lang.core.resolve.VisibilityStatus.Visible
import org.sui.lang.core.resolve2.createFilter
import org.sui.lang.core.resolve2.namespace
import org.sui.lang.core.resolve2.visInfo
import org.sui.lang.index.MvNamedElementIndex
import org.sui.lang.moveProject

object ImportCandidateCollector {

    fun getImportCandidates(context: ImportContext, targetName: String): List<ImportCandidate> {
        val (pathElement, namespaces) = context

        val project = pathElement.project
        val moveProject = pathElement.moveProject ?: return emptyList()
        val searchScope = moveProject.searchScope()

        val allItems = mutableListOf<MvQualNamedElement>()
//        if (isUnitTestMode) {
//            // always add current file in tests
//            val currentFile = pathElement.containingFile as? MoveFile ?: return emptyList()
//            val items = mutableListOf<MvQualNamedElement>()
//            processFileItemsForUnitTests(currentFile, namespaces, visibilities, itemVis, createProcessor {
//                val element = it.element
//                if (element is MvQualNamedElement && it.name == targetName) {
//                    items.add(element)
//                }
//            })
//            allItems.addAll(items)
//        }

        MvNamedElementIndex
            .processElementsByName(project, targetName, searchScope) { element ->
                val elementNs = element.namespace
                if (elementNs !in namespaces) return@processElementsByName true
                val visibilityFilter = element.visInfo().createFilter()

                val visibilityStatus = visibilityFilter.filter(pathElement, namespaces)
                if (visibilityStatus != Visible) return@processElementsByName true

                if (element !is MvQualNamedElement) return@processElementsByName true
                if (element.name == targetName) {
                    allItems.add(element)
                }

//                processQualItem(element, namespaces, visibilities, itemVis) {
//                    val entryElement = it.element
//                    if (entryElement !is MvQualNamedElement) return@processQualItem true
//                    if (it.name == targetName) {
//                        allItems.add(entryElement)
//                    }
//                    false
//                }
                true
            }

        return allItems
//            .filter(itemFilter)
            .mapNotNull { item -> item.qualName?.let { ImportCandidate(item, it) } }
    }

    fun getCompletionCandidates(
        parameters: CompletionParameters,
        prefixMatcher: PrefixMatcher,
        processedPathNames: Set<String>,
        importContext: ImportContext,
        itemFilter: (PsiElement) -> Boolean = { true }
    ): List<ImportCandidate> {
        val project = parameters.position.project
        val keys = hashSetOf<String>().apply {
            val names = MvNamedElementIndex.getAllKeys(project)
            addAll(names)
            removeAll(processedPathNames)
        }

        return prefixMatcher.sortMatching(keys)
            .flatMap {
                getImportCandidates(importContext, it)
                    .distinctBy { it.element }
                    .filter { itemFilter(it.element) }
            }
    }
}