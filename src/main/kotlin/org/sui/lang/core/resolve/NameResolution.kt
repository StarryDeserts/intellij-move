package org.sui.lang.core.resolve

import com.intellij.psi.search.GlobalSearchScope
import org.sui.cli.containingMovePackage
import org.sui.cli.settings.moveSettings
import org.sui.lang.MoveFile
import org.sui.lang.core.psi.*
import org.sui.lang.core.psi.NamedItemScope.MAIN
import org.sui.lang.core.psi.NamedItemScope.VERIFY
import org.sui.lang.core.psi.ext.*
import org.sui.lang.core.resolve.LetStmtScope.EXPR_STMT
import org.sui.lang.core.resolve.LetStmtScope.NONE
import org.sui.lang.core.resolve.ref.MvReferenceElement
import org.sui.lang.core.resolve.ref.Namespace
import org.sui.lang.core.resolve.ref.Visibility
import org.sui.lang.core.types.address
import org.sui.lang.index.MvNamedElementIndex
import org.sui.lang.moveProject
import org.sui.lang.preLoadItems
import org.sui.lang.toNioPathOrNull
import org.sui.stdext.wrapWithList

data class ContextScopeInfo(
    val refItemScopes: Set<NamedItemScope>,
    val letStmtScope: LetStmtScope,
) {
    val isMslScope get() = letStmtScope != LetStmtScope.NONE

    fun matches(itemElement: MvNamedElement): Boolean {
        if (
            !this.isMslScope && itemElement.isMslOnlyItem
        ) return false
        if (!itemElement.isVisibleInContext(this.refItemScopes)) return false
        return true
    }

    companion object {
        /// really does not affect anything, created just to allow creating CompletionContext everywhere
        fun default(): ContextScopeInfo = ContextScopeInfo(setOf(MAIN), NONE)
        fun msl(): ContextScopeInfo = ContextScopeInfo(setOf(VERIFY), EXPR_STMT)
    }
}

fun processItems(
    element: MvElement,
    namespaces: Set<Namespace>,
    contextScopeInfo: ContextScopeInfo,
    processor: MatchingProcessor<MvNamedElement>,
): Boolean {
    return walkUpThroughScopes(
        element,
        stopAfter = { it is MvModule }
    ) { cameFrom, scope ->
        processItemsInScope(
            scope, cameFrom, namespaces, contextScopeInfo, processor
        )
    }
}

fun resolveLocalItem(
    element: MvReferenceElement,
    namespaces: Set<Namespace>
): List<MvNamedElement> {
    val contextScopeInfo =
        ContextScopeInfo(
            letStmtScope = element.letStmtScope,
            refItemScopes = element.refItemScopes,
        )
    val referenceName = element.referenceName
    var resolved: MvNamedElement? = null
    processItems(element, namespaces, contextScopeInfo) {
        if (it.name == referenceName) {
            resolved = it.element
            return@processItems true
        }
        return@processItems false
    }
    return resolved.wrapWithList()
}

// go from local MODULE reference to corresponding FqModuleRef (from import)
fun resolveIntoFQModuleRefInUseSpeck(moduleRef: MvModuleRef): MvFQModuleRef? {
    check(moduleRef !is MvFQModuleRef) { "Should be handled on the upper level" }

    // module refers to ModuleImport
    var resolved = resolveLocalItem(moduleRef, setOf(Namespace.MODULE)).firstOrNull()
    if (resolved is MvUseAlias) {
        resolved = resolved.moduleUseSpeck ?: resolved.useItem
    }
    if (resolved is MvUseItem && resolved.isSelf) {
        return resolved.itemUseSpeck.fqModuleRef
    }
//    if (resolved !is MvModuleUseSpeck) return null
    return (resolved as? MvModuleUseSpeck)?.fqModuleRef
//    return resolved.fqModuleRef
}

fun processQualItem(
    item: MvNamedElement,
    namespaces: Set<Namespace>,
    visibilities: Set<Visibility>,
    contextScopeInfo: ContextScopeInfo,
    processor: MatchingProcessor<MvNamedElement>,
): Boolean {
    val matched = when {
        item is MvModule && Namespace.MODULE in namespaces
                || item is MvStruct && Namespace.TYPE in namespaces
                || item is MvSchema && Namespace.SCHEMA in namespaces ->
            processor.match(contextScopeInfo, item)

        item is MvFunction && Namespace.FUNCTION in namespaces -> {
            if (item.hasTestAttr) return false
            for (vis in visibilities) {
                when {
                    vis is Visibility.Public
                            && item.visibility == FunctionVisibility.PUBLIC -> processor.match(
                        contextScopeInfo,
                        item
                    )

                    vis is Visibility.PublicScript
                            && item.visibility == FunctionVisibility.PUBLIC_SCRIPT ->
                        processor.match(contextScopeInfo, item)

                    vis is Visibility.PublicFriend && item.visibility == FunctionVisibility.PUBLIC_FRIEND -> {
                        val itemModule = item.module ?: return false
                        val currentModule = vis.currentModule.element ?: return false
                        if (currentModule.fqModule() in itemModule.declaredFriendModules) {
                            processor.match(contextScopeInfo, item)
                        }
                    }

                    vis is Visibility.PublicPackage && item.visibility == FunctionVisibility.PUBLIC_PACKAGE -> {
                        if (!item.project.moveSettings.enablePublicPackage) {
                            return false
                        }
                        val itemPackage = item.containingMovePackage ?: return false
                        if (vis.originPackage == itemPackage) {
                            processor.match(contextScopeInfo, item)
                        }
                    }

                    vis is Visibility.Internal -> processor.match(contextScopeInfo, item)
                }
            }
            false
        }
        else -> false
    }
    return matched
}

fun processModulesInFile(file: MoveFile, moduleProcessor: MatchingProcessor<MvNamedElement>): Boolean {
    for (module in file.modules()) {
        if (moduleProcessor.match(module)) return true
    }
    return false
}

fun processFQModuleRef(
    fqModuleRef: MvFQModuleRef,
    processor: MatchingProcessor<MvModule>,
) {
    val moveProj = fqModuleRef.moveProject ?: return
    val refAddressText = fqModuleRef.addressRef.address(moveProj)?.canonicalValue(moveProj)

    val contextScopeInfo = ContextScopeInfo(
        letStmtScope = fqModuleRef.letStmtScope,
        refItemScopes = fqModuleRef.refItemScopes,
    )
    val moduleProcessor = MatchingProcessor {
        if (!contextScopeInfo.matches(it.element)) {
            return@MatchingProcessor false
        }
        val entry = ScopeItem(it.name, it.element as MvModule)
        val modAddressText = entry.element.address(moveProj)?.canonicalValue(moveProj)
        if (modAddressText != refAddressText)
            return@MatchingProcessor false
        processor.match(entry)
    }

    // first search modules in the current file
    val currentFile = fqModuleRef.containingMoveFile ?: return
    var stopped = processModulesInFile(currentFile, moduleProcessor)
//    var stopped =
//        processFileItems(currentFile, setOf(Namespace.MODULE), Visibility.local(), itemVis, moduleProcessor)
    if (stopped) return

    moveProj.processMoveFiles { moveFile ->
        // skip current file as it's processed already
        if (moveFile.toNioPathOrNull() == currentFile.toNioPathOrNull())
            return@processMoveFiles true
        stopped = processModulesInFile(moveFile, moduleProcessor)
        // if not resolved, returns true to indicate that next file should be tried
        !stopped
    }
}

fun processFQModuleRef(
    moduleRef: MvFQModuleRef,
    target: String,
    processor: MatchingProcessor<MvModule>,
) {
    val project = moduleRef.project
    val moveProj = moduleRef.moveProject ?: return
    val refAddress = moduleRef.addressRef.address(moveProj)?.canonicalValue(moveProj)

    val contextScopeInfo = ContextScopeInfo(
        letStmtScope = moduleRef.letStmtScope,
        refItemScopes = moduleRef.refItemScopes,
    )
    val matchModule = MatchingProcessor {
        if (!contextScopeInfo.matches(it.element)) return@MatchingProcessor false
        val entry = ScopeItem(it.name, it.element as MvModule)
        // TODO: check belongs to the current project
        val modAddress = entry.element.address(moveProj)?.canonicalValue(moveProj)

        if (modAddress != refAddress) return@MatchingProcessor false
        processor.match(entry)
    }


    // search modules in the current file first
    val currentFile = moduleRef.containingMoveFile ?: return
    val stopped = processModulesInFile(currentFile, matchModule)
//        processFileItems(currentFile, setOf(Namespace.MODULE), Visibility.local(), itemVis, matchModule)
    if (stopped) return

    val currentFileScope = GlobalSearchScope.fileScope(currentFile)
    val searchScope =
        moveProj.searchScope().intersectWith(GlobalSearchScope.notScope(currentFileScope))

    MvNamedElementIndex
        .processElementsByName(project, target, searchScope) {
            val matched =
                processQualItem(it, setOf(Namespace.MODULE), Visibility.local(), contextScopeInfo, matchModule)
            if (matched) return@processElementsByName false

            true
        }
}

fun processPreLoadModuleItem(
    element: MvPath,
    processor: MatchingProcessor<MvStruct>,
) {
    val moveProj = element.moveProject ?: return
    val contextScopeInfo = ContextScopeInfo(
        letStmtScope = element.letStmtScope,
        refItemScopes = element.itemScopes,
    )
    val moduleProcessor = MatchingProcessor {
        if (!contextScopeInfo.matches(it.element)) {
            return@MatchingProcessor false
        }
        val entry = ScopeItem(it.name, it.element as MvStruct)
        processor.match(entry)
    }
    var stopped = false
    moveProj.processMoveFiles { moveFile ->
        if (moveFile.name == "object.move" || moveFile.name == "tx_context.move") {
            stopped = processPreLoadModuleItem(moveFile, moduleProcessor)
            !stopped
        } else {
            true
        }
    }
}

fun processPreLoadModuleItem(file: MoveFile, moduleProcessor: MatchingProcessor<MvNamedElement>): Boolean {
    for (item in file.preLoadItems()) {
        if (moduleProcessor.match(item)) return true
    }
    return false
}

fun processModuleRef(
    moduleRef: MvModuleRef,
    processor: MatchingProcessor<MvModule>,
) {
    val moveProj = moduleRef.moveProject ?: return
    val contextScopeInfo = ContextScopeInfo(
        letStmtScope = moduleRef.letStmtScope,
        refItemScopes = moduleRef.refItemScopes,
    )
    val moduleProcessor = MatchingProcessor {
        if (!contextScopeInfo.matches(it.element)) {
            return@MatchingProcessor false
        }
        val entry = ScopeItem(it.name, it.element as MvModule)
        processor.match(entry)
    }
    var stopped = false
    moveProj.processMoveFiles { moveFile ->
        stopped = processPreLoadModules(moveFile, moduleProcessor)
        !stopped
    }
}

fun processPreLoadModules(file: MoveFile, moduleProcessor: MatchingProcessor<MvNamedElement>): Boolean {
    if (listOf("transfer.move", "object.move", "option.move", "vector.move", "tx_context.move").contains(file.name)) {
        for (module in file.preloadModules()) {
            if (moduleProcessor.match(module)) return true
        }
    }
    return false
}

fun walkUpThroughScopes(
    start: MvElement,
    stopAfter: (MvElement) -> Boolean,
    handleScope: (cameFrom: MvElement, scope: MvElement) -> Boolean,
): Boolean {
    var cameFrom = start
    var scope = start.context as MvElement?
    while (scope != null) {
        if (handleScope(cameFrom, scope)) return true

        // walk all items in original module block
        if (scope is MvModuleBlock) {
            // handle spec module {}
            if (handleModuleItemSpecsInBlock(cameFrom, scope, handleScope)) return true
            // walk over all spec modules
            for (moduleSpec in scope.module.allModuleSpecs()) {
                val moduleSpecBlock = moduleSpec.moduleSpecBlock ?: continue
                if (handleScope(cameFrom, moduleSpecBlock)) return true
                if (handleModuleItemSpecsInBlock(cameFrom, moduleSpecBlock, handleScope)) return true
            }
        }

        if (scope is MvModuleSpecBlock) {
            val moduleBlock = scope.moduleSpec.moduleItem?.moduleBlock
            if (moduleBlock != null) {
                cameFrom = scope
                scope = moduleBlock
                continue
            }
        }

        if (stopAfter(scope)) break

        cameFrom = scope
        scope = scope.context as? MvElement
    }

    return false
}

private fun handleModuleItemSpecsInBlock(
    cameFrom: MvElement,
    block: MvElement,
    handleScope: (cameFrom: MvElement, scope: MvElement) -> Boolean
): Boolean {
    val moduleItemSpecs = when (block) {
        is MvModuleBlock -> block.moduleItemSpecList
        is MvModuleSpecBlock -> block.moduleItemSpecList
        else -> emptyList()
    }
    for (moduleItemSpec in moduleItemSpecs.filter { it != cameFrom }) {
        val itemSpecBlock = moduleItemSpec.itemSpecBlock ?: continue
        if (handleScope(cameFrom, itemSpecBlock)) return true
    }
    return false
}
