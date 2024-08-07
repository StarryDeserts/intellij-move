package org.sui.lang.core.completion.providers

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.sui.ide.inspections.imports.ImportContext
import org.sui.lang.core.MvPsiPatterns
import org.sui.lang.core.completion.CompletionContext
import org.sui.lang.core.completion.UNIMPORTED_ITEM_PRIORITY
import org.sui.lang.core.completion.createLookupElement
import org.sui.lang.core.psi.*
import org.sui.lang.core.psi.ext.ancestors
import org.sui.lang.core.psi.ext.endOffset
import org.sui.lang.core.psi.ext.isMslScope
import org.sui.lang.core.psi.ext.isSelfModuleRef
import org.sui.lang.core.resolve.*
import org.sui.lang.core.resolve.ref.MvReferenceElement
import org.sui.lang.core.resolve.ref.Namespace
import org.sui.lang.core.resolve.ref.Visibility
import org.sui.lang.core.types.infer.inferExpectedTy
import org.sui.lang.core.types.infer.inference
import org.sui.lang.core.types.ty.Ty
import org.sui.lang.core.types.ty.TyUnknown

abstract class MvPathCompletionProvider : MvCompletionProvider() {

    abstract val namespace: Namespace

    open fun pathScopeInfo(pathElement: MvPath): ContextScopeInfo =
        ContextScopeInfo(
            letStmtScope = pathElement.letStmtScope,
            refItemScopes = pathElement.refItemScopes,
        )

    final override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val maybePath = parameters.position.parent
        val pathElement = maybePath as? MvPath ?: maybePath.parent as MvPath

        if (parameters.position !== pathElement.referenceNameElement) return

        val moduleRef = pathElement.moduleRef
        val namespaces = setOf(this.namespace)
        val pathScopeInfo = pathScopeInfo(pathElement)
        val msl = pathElement.isMslScope
        val expectedTy = getExpectedTypeForEnclosingPathOrDotExpr(pathElement, msl)

        val structAsType = this.namespace == Namespace.TYPE
        val ctx = CompletionContext(
            pathElement,
            pathScopeInfo,
            expectedTy
        )

        if (moduleRef != null) {
            val module = moduleRef.reference?.resolveWithAliases() as? MvModule
                ?: return
            val vs = when {
                moduleRef.isSelfModuleRef -> setOf(Visibility.Internal)
                else -> Visibility.visibilityScopesForElement(pathElement)
            }
            processModuleItems(module, namespaces, vs, pathScopeInfo) {
                val lookup =
                    it.element.createLookupElement(ctx, structAsType = structAsType)
                result.addElement(lookup)
                false
            }
            return
        }

        val processedNames = mutableSetOf<String>()
        processItems(pathElement, namespaces, pathScopeInfo) { (name, element) ->
            if (processedNames.contains(name)) {
                return@processItems false
            }
            processedNames.add(name)
            result.addElement(
                element.createLookupElement(
                    ctx,
                    structAsType = structAsType,
                    priority = element.completionPriority
                )
            )
            false
        }

        // disable auto-import in module specs for now
        if (pathElement.containingModuleSpec != null) return

        val originalPathElement = parameters.originalPosition?.parent as? MvPath ?: return
        val importContext =
            ImportContext.from(
                originalPathElement,
                namespaces,
                setOf(Visibility.Public),
                pathScopeInfo
            )
        val candidates = getImportCandidates(
            parameters,
            result,
            processedNames,
            importContext,
        )
        candidates.forEach { candidate ->
            val lookupElement = candidate.element.createLookupElement(
                ctx,
                structAsType = structAsType,
                priority = UNIMPORTED_ITEM_PRIORITY,
                insertHandler = ImportInsertHandler(parameters, candidate)
            )
            result.addElement(lookupElement)
        }
    }
}

object NamesCompletionProvider : MvPathCompletionProvider() {
    override val elementPattern: ElementPattern<PsiElement>
        get() =
            MvPsiPatterns.path()
                .andNot(MvPsiPatterns.pathType())
                .andNot(MvPsiPatterns.schemaLit())

    override val namespace: Namespace get() = Namespace.NAME

//    override fun itemVis(pathElement: MvPath): ItemVis {
//        return ItemVis(
//            setOf(Namespace.NAME),
//            Visibility.none(),
//            mslLetScope = pathElement.mslLetScope,
//            itemScopes = pathElement.itemScopes,
//        )
//    }
}

object FunctionsCompletionProvider : MvPathCompletionProvider() {
    override val elementPattern: ElementPattern<PsiElement>
        get() =
            MvPsiPatterns.path()
                .andNot(MvPsiPatterns.pathType())
                .andNot(MvPsiPatterns.schemaLit())

    override val namespace: Namespace get() = Namespace.FUNCTION

//    override fun itemVis(pathElement: MvPath): ItemVis {
//        return ItemVis(
//            setOf(Namespace.FUNCTION),
//            Visibility.none(),
//            mslLetScope = pathElement.mslLetScope,
//            itemScopes = pathElement.itemScopes,
//        )
//    }
}

object TypesCompletionProvider : MvPathCompletionProvider() {
    override val elementPattern: ElementPattern<out PsiElement>
        get() = MvPsiPatterns.pathType()

    override val namespace: Namespace get() = Namespace.TYPE

//    override fun itemVis(pathElement: MvPath): ItemVis {
//        return ItemVis(
//            setOf(Namespace.TYPE),
//            Visibility.none(),
//            mslLetScope = pathElement.mslLetScope,
//            itemScopes = pathElement.itemScopes,
//        )
//    }
}

object SchemasCompletionProvider : MvPathCompletionProvider() {
    override val elementPattern: ElementPattern<PsiElement>
        get() =
            StandardPatterns.or(
                MvPsiPatterns.schemaLit(), MvPsiPatterns.pathInsideIncludeStmt()
            )

    override val namespace: Namespace get() = Namespace.SCHEMA

    override fun pathScopeInfo(pathElement: MvPath): ContextScopeInfo {
        return ContextScopeInfo(
            letStmtScope = LetStmtScope.EXPR_STMT,
            refItemScopes = pathElement.refItemScopes,
        )
    }
}

fun getExpectedTypeForEnclosingPathOrDotExpr(element: MvReferenceElement, msl: Boolean): Ty? {
    for (ancestor in element.ancestors) {
        if (element.endOffset < ancestor.endOffset) continue
        if (element.endOffset > ancestor.endOffset) break
        when (ancestor) {
            is MvPathType,
            is MvRefExpr,
            is MvDotExpr -> {
                val inference = (ancestor as MvElement).inference(msl) ?: return TyUnknown
                return inferExpectedTy(ancestor, inference)
            }
        }
    }
    return null
}
