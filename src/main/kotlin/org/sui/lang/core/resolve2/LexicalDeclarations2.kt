package org.sui.lang.core.resolve2

import com.intellij.psi.util.PsiTreeUtil
import org.sui.ide.inspections.imports.usageScope
import org.sui.lang.core.psi.*
import org.sui.lang.core.psi.ext.*
import org.sui.lang.core.resolve.*
import org.sui.lang.core.resolve.LetStmtScope.*
import org.sui.lang.core.resolve.ref.NAMES
import org.sui.lang.core.resolve.ref.NONE
import org.sui.lang.core.resolve.ref.Namespace
import org.sui.lang.core.resolve.ref.Namespace.NAME
import org.sui.lang.core.resolve.ref.TYPES
import org.sui.lang.core.resolve2.ref.ResolutionContext
import org.sui.lang.core.resolve2.util.forEachLeafSpeck

fun processItemsInScope(
    scope: MvElement,
    cameFrom: MvElement,
    ns: Set<Namespace>,
    ctx: ResolutionContext,
    processor: RsResolveProcessor,
): Boolean {
    for (namespace in ns) {
        val stop = when (namespace) {
            NAME -> {
                val found = when (scope) {
                    is MvModule -> {
//                        val module = scope.parent as MvModule
                        processor.processAllItems(
                            ns,
                            scope.structs(),
                            scope.consts(),
                        )
                    }

                    is MvModuleSpecBlock -> processor.processAllItems(ns, scope.schemaList)
                    is MvScript -> processor.processAllItems(ns, scope.constList)
                    is MvFunctionLike -> processor.processAll(scope.allParamsAsBindings)
                    is MvLambdaExpr -> processor.processAll(scope.bindingPatList)
                    is MvForExpr -> {
                        val iterConditionBindingPat = scope.forIterCondition?.bindingPat
                        if (iterConditionBindingPat != null) {
                            processor.process(iterConditionBindingPat, NAMES)
                        } else {
                            false
                        }
                    }

                    is MvMatchArm -> {
                        if (cameFrom is MvMatchPat) continue
                        // only use those bindings for the match arm rhs
                        processor.processAll(scope.matchPat.pat.bindings.toList())
                    }

                    is MvItemSpec -> {
                        val item = scope.item
                        when (item) {
                            is MvFunction -> {
                                processor.processAll(
                                    item.valueParamsAsBindings,
                                    item.specResultParameters.map { it.bindingPat },
                                    ns = NAMES
                                )
                            }

                            is MvStruct -> processor.processAll(item.fields)
                            else -> false
                        }
                    }

                    is MvSchema -> processor.processAll(scope.fieldBindings)
                    is MvQuantBindingsOwner -> processor.processAll(scope.bindings)
                    is MvCodeBlock,
                    is MvSpecCodeBlock -> {
                        val visibleLetStmts = when (scope) {
                            is MvCodeBlock -> {
                                scope.letStmts
                                    // drops all let-statements after the current position
                                    .filter { it.cameBefore(cameFrom) }
                                    // drops let-statement that is ancestors of ref (on the same statement, at most one)
                                    .filter {
                                        cameFrom != it
                                                && !PsiTreeUtil.isAncestor(cameFrom, it, true)
                                    }
                            }

                            is MvSpecCodeBlock -> {
                                val letStmtScope = ctx.element.letStmtScope
                                when (letStmtScope) {
                                    EXPR_STMT -> scope.allLetStmts
                                    LET_STMT, LET_POST_STMT -> {
                                        val letDecls =
                                            if (letStmtScope == LET_POST_STMT) {
                                                scope.allLetStmts
                                            } else {
                                                scope.letStmts(false)
                                            }
                                        letDecls
                                            // drops all let-statements after the current position
                                            .filter { it.cameBefore(cameFrom) }
                                            // drops let-statement that is ancestors of ref (on the same statement, at most one)
                                            .filter {
                                                cameFrom != it
                                                        && !PsiTreeUtil.isAncestor(cameFrom, it, true)
                                            }
                                    }

                                    NOT_MSL -> emptyList()
                                }
                            }

                            else -> error("unreachable")
                        }
                        // shadowing support (look at latest first)
                        val letBindings = visibleLetStmts
                            .asReversed()
                            .flatMap { it.pat?.bindings.orEmpty() }

                        // skip shadowed (already visited) elements
                        val visited = mutableSetOf<String>()
                        val variablesProcessor = processor.wrapWithFilter {
                            val isVisited = it.name in visited
                            if (!isVisited) {
                                visited += it.name
                            }
                            !isVisited
                        }
                        var found = variablesProcessor.processAll(letBindings, NAMES)
                        if (!found && scope is MvSpecCodeBlock) {
                            // if inside SpecCodeBlock, process also with builtin spec consts and global variables
                            found = variablesProcessor.processAllItems(
                                ns,
                                scope.builtinSpecConsts(),
                                scope.globalVariables()
                            )
                        }
                        found
                    }

                    else -> false
                }
                found
            }

            Namespace.FUNCTION -> {
                val found = when (scope) {
                    is MvModule -> {
                        val specFunctions =
                            listOf(scope.specFunctions(), scope.builtinSpecFunctions()).flatten()
                        val specInlineFunctions = scope.moduleItemSpecList.flatMap { it.specInlineFunctions() }
                        processor.processAllItems(
                            ns,
                            scope.builtinFunctions(),
                            scope.allNonTestFunctions(),
                            specFunctions,
                            specInlineFunctions
                        )
                    }

                    is MvModuleSpecBlock -> {
                        val specFunctions = scope.specFunctionList
                        val specInlineFunctions = scope.moduleItemSpecList.flatMap { it.specInlineFunctions() }
                        processor.processAllItems(
                            ns,
                            specFunctions,
                            specInlineFunctions
                        )
                    }

                    is MvFunctionLike -> processor.processAll(scope.lambdaParamsAsBindings)
                    is MvLambdaExpr -> processor.processAll(scope.bindingPatList)
                    is MvItemSpec -> {
                        val item = scope.item
                        when (item) {
                            is MvFunction -> processor.processAll(item.lambdaParamsAsBindings)
                            else -> false
                        }
                    }

                    is MvSpecCodeBlock -> {
                        val inlineFunctions = scope.specInlineFunctions().asReversed()
                        processor.processAllItems(ns, inlineFunctions)
                    }

                    else -> false
                }
                found
            }

            Namespace.TYPE -> {
                if (scope is MvTypeParametersOwner) {
                    if (processor.processAll(scope.typeParameters, TYPES)) return true
                }
                val found = when (scope) {
                    is MvItemSpec -> {
                        val funcItem = scope.funcItem
                        if (funcItem != null) {
                            processor.processAll(funcItem.typeParameters, TYPES)
                        } else {
                            false
                        }
                    }

                    is MvModule -> {
                        processor.processAllItems(
                            ns,
                            scope.structs(),
                            scope.enumList
                        )
                    }

                    is MvApplySchemaStmt -> {
                        val toPatterns = scope.applyTo?.functionPatternList.orEmpty()
                        val patternTypeParams =
                            toPatterns.flatMap { it.typeParameterList?.typeParameterList.orEmpty() }
                        processor.processAll(patternTypeParams)
                    }

                    else -> false
                }
                found
            }

            Namespace.SCHEMA -> when (scope) {
                is MvModule -> processor.processAllItems(ns, scope.schemaList)
                is MvModuleSpecBlock -> processor.processAllItems(ns, scope.schemaList, scope.specFunctionList)
                else -> false
            }

            else -> false
        }
        if (stop) return true
    }

    if (scope is MvItemsOwner) {
        if (scope.processUseSpeckElements(ns, processor)) return true
    }

    return false
}

private fun MvItemsOwner.processUseSpeckElements(ns: Set<Namespace>, processor: RsResolveProcessor): Boolean {
    var stop = false
    for (useStmt in this.useStmtList) {
        val stmtUsageScope = useStmt.usageScope
        useStmt.forEachLeafSpeck { speckPath, alias ->
            val name = if (alias != null) {
                alias.name ?: return@forEachLeafSpeck false
            } else {
                var n = speckPath.referenceName ?: return@forEachLeafSpeck false
                // 0x1::m::Self -> 0x1::m
                if (n == "Self") {
                    n = speckPath.qualifier?.referenceName ?: return@forEachLeafSpeck false
                }
                n
            }
            val resolvedItem = speckPath.reference?.resolve()
            if (resolvedItem == null) {
                if (alias != null) {
                    // aliased element cannot be resolved, but alias itself is valid, resolve to it
                    if (processor.process(name, NONE, alias)) return@forEachLeafSpeck true
                }
                // todo: should it be resolved to import anyway?
                return@forEachLeafSpeck false
            }

            val element = alias ?: resolvedItem
            val namespace = resolvedItem.namespace
            val visibilityFilter =
                resolvedItem.visInfo(adjustScope = stmtUsageScope).createFilter()

            if (namespace in ns && processor.process(name, element, ns, visibilityFilter)) {
                stop = true
                return@forEachLeafSpeck true
            }
            false
        }
        if (stop) return true
    }
    return stop
}
