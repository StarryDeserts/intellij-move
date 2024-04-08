package org.sui.ide.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.util.descendantsOfType
import org.sui.cli.settings.isDebugModeEnabled
import org.sui.ide.inspections.imports.AutoImportFix
import org.sui.lang.core.psi.*
import org.sui.lang.core.psi.ext.*
import org.sui.lang.core.resolve.ref.MvReferenceElement
import org.sui.lang.core.types.infer.inference
import org.sui.lang.core.types.ty.TyUnknown

class MvUnresolvedReferenceInspection : MvLocalInspectionTool() {

    var ignoreWithoutQuickFix: Boolean = false

    override val isSyntaxOnly get() = false

    private fun ProblemsHolder.registerUnresolvedReferenceError(element: MvReferenceElement) {
        // no errors in pragmas
        if (element.hasAncestor<MvPragmaSpecStmt>()) return

        val candidates = AutoImportFix.findApplicableContext(element)?.candidates.orEmpty()
        if (candidates.isEmpty() && ignoreWithoutQuickFix) return

        val referenceName = element.referenceName ?: return
        val parent = element.parent
        val description = when (parent) {
            is MvPathType -> "Unresolved type: `$referenceName`"
            is MvCallExpr -> "Unresolved function: `$referenceName`"
            else -> "Unresolved reference: `$referenceName`"
        }

        val highlightedElement = element.referenceNameElement ?: element
        val fix = if (candidates.isNotEmpty()) AutoImportFix(element) else null
        registerProblem(
            highlightedElement,
            description,
            ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
            *listOfNotNull(fix).toTypedArray()
        )
    }

    override fun buildMvVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : MvVisitor() {
        override fun visitModuleRef(moduleRef: MvModuleRef) {
            if (moduleRef.isMslScope && !moduleRef.project.isDebugModeEnabled) {
                return
            }
            // skip this check, as it will be checked in MvPath visitor
            if (moduleRef.ancestorStrict<MvPath>() != null) return

            // skip those two, checked in UseSpeck checks later
            if (moduleRef.ancestorStrict<MvUseStmt>() != null) return
            if (moduleRef is MvFQModuleRef) return

            if (moduleRef.unresolved) {
                holder.registerUnresolvedReferenceError(moduleRef)
            }
        }

        override fun visitPath(path: MvPath) {
            // skip specs in non-dev mode, too many false-positives
            if (path.isMslScope && !path.project.isDebugModeEnabled) {
                return
            }
//            if (path.isMslLegacy() && path.isResult) return
            if (path.isMslScope && path.isSpecPrimitiveType()) return
            if (path.isUpdateFieldArg2) return

            if (path.isPrimitiveType()) return
            // destructuring assignment like `Coin { val1: _ } = get_coin()`
            if (path.textMatches("_") && path.isInsideAssignmentLhs()) return
            // assert macro
            if (path.text == "assert") return
            // attribute values are special case
            if (path.hasAncestor<MvAttrItem>()) return

            val moduleRef = path.moduleRef
            if (moduleRef != null) {
                if (moduleRef is MvFQModuleRef) return
                if (moduleRef.unresolved) {
                    holder.registerUnresolvedReferenceError(moduleRef)
                    return
                }
            }
            if (path.unresolved) {
                holder.registerUnresolvedReferenceError(path)
            }
        }

        override fun visitStructPatField(patField: MvStructPatField) {
            if (patField.isMsl() && !patField.project.isDebugModeEnabled) {
                return
            }
            val resolvedStructDef = patField.structPat.path.maybeStruct ?: return
            if (!resolvedStructDef.fieldNames.any { it == patField.referenceName }) {
                holder.registerProblem(
                    patField.referenceNameElement,
                    "Unresolved field: `${patField.referenceName}`",
                    ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
                )
            }
        }

        override fun visitStructLitField(litField: MvStructLitField) {
            if (litField.isMsl() && !litField.project.isDebugModeEnabled) {
                return
            }
            if (litField.isShorthand) {
                val resolvedItems = litField.reference.multiResolve()
                val resolvedStructField = resolvedItems.find { it is MvStructField }
                if (resolvedStructField == null) {
                    holder.registerProblem(
                        litField.referenceNameElement,
                        "Unresolved field: `${litField.referenceName}`",
                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
                    )
                }
                val resolvedBinding = resolvedItems.find { it is MvBindingPat }
                if (resolvedBinding == null) {
                    holder.registerProblem(
                        litField.referenceNameElement,
                        "Unresolved reference: `${litField.referenceName}`",
                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
                    )
                }
            } else {
                if (litField.reference.resolve() == null) {
                    holder.registerProblem(
                        litField.referenceNameElement,
                        "Unresolved field: `${litField.referenceName}`",
                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
                    )
                }
            }
        }

        override fun visitSchemaLitField(field: MvSchemaLitField) {
            if (field.isShorthand) {
                val resolvedItems = field.reference.multiResolve()
                val fieldBinding = resolvedItems.find { it is MvBindingPat && it.owner is MvSchemaFieldStmt }
                if (fieldBinding == null) {
                    holder.registerProblem(
                        field.referenceNameElement,
                        "Unresolved field: `${field.referenceName}`",
                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
                    )
                }
                val letBinding = resolvedItems.find { it is MvBindingPat }
                if (letBinding == null) {
                    holder.registerProblem(
                        field.referenceNameElement,
                        "Unresolved reference: `${field.referenceName}`",
                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
                    )
                }
            } else {
                if (field.reference.resolve() == null) {
                    holder.registerProblem(
                        field.referenceNameElement,
                        "Unresolved field: `${field.referenceName}`",
                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
                    )
                }
            }
        }

        override fun visitDotExpr(dotExpr: MvDotExpr) {
            if (dotExpr.isMsl() && !dotExpr.project.isDebugModeEnabled) {
                return
            }
            val receiverTy = dotExpr.inference(false)?.getExprType(dotExpr.expr)
            // disable inspection is object is unresolved
            if (receiverTy is TyUnknown) return

            val dotField = dotExpr.structDotField ?: return
            if (!dotField.resolvable) {
                holder.registerProblem(
                    dotField.referenceNameElement,
                    "Unresolved field: `${dotField.referenceName}`",
                    ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
                )
            }
        }

        override fun visitModuleUseSpeck(o: MvModuleUseSpeck) {
            val moduleRef = o.fqModuleRef ?: return
            if (!moduleRef.resolvable) {
                val refNameElement = moduleRef.referenceNameElement ?: return
                holder.registerProblem(
                    refNameElement,
                    "Unresolved reference: `${refNameElement.text}`",
                    ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
                )
            }
        }

        override fun visitItemUseSpeck(o: MvItemUseSpeck) {
            val moduleRef = o.fqModuleRef
            if (!moduleRef.resolvable) {
                val refNameElement = moduleRef.referenceNameElement ?: return
                holder.registerProblem(
                    refNameElement,
                    "Unresolved reference: `${refNameElement.text}`",
                    ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
                )
                return
            }
            val useItems = o.descendantsOfType<MvUseItem>()
            for (useItem in useItems) {
                if (!useItem.resolvable) {
                    val refNameElement = useItem.referenceNameElement
                    holder.registerProblem(
                        refNameElement,
                        "Unresolved reference: `${refNameElement.text}`",
                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
                    )
                }
            }
        }
    }
}
