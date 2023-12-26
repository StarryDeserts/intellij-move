package org.sui.ide.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.sui.ide.inspections.fixes.FieldShorthandFix
import org.sui.lang.core.psi.*

class SuiFieldInitShorthandInspection : MvLocalInspectionTool() {
    override val isSyntaxOnly: Boolean get() = true

    override fun buildMvVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : MvVisitor() {
        override fun visitStructLitField(field: MvStructLitField) {
            val initExpr = field.expr ?: return
            if (!(initExpr is MvRefExpr && initExpr.text == field.identifier.text)) return
            holder.registerProblem(
                field,
                "Expression can be simplified",
                ProblemHighlightType.WEAK_WARNING,
                FieldShorthandFix.StructLit(field),
            )
        }

        override fun visitStructPatField(field: MvStructPatField) {
            val ident = field.identifier ?: return
            val fieldBinding = field.structPatFieldBinding ?: return
            if (ident.text == fieldBinding.pat.text.orEmpty()) {
                holder.registerProblem(
                    field,
                    "Expression can be simplified",
                    ProblemHighlightType.WEAK_WARNING,
                    FieldShorthandFix.StructPat(field)
                )
            }
        }

        override fun visitSchemaLitField(schemaField: MvSchemaLitField) {
            val initExpr = schemaField.expr ?: return
            if (!(initExpr is MvRefExpr && initExpr.text == schemaField.identifier.text)) return
            holder.registerProblem(
                schemaField,
                "Expression can be simplified",
                ProblemHighlightType.WEAK_WARNING,
                FieldShorthandFix.Schema(schemaField)
            )

        }
    }
}
