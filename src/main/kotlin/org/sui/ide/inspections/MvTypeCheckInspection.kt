package org.sui.ide.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.util.descendantsOfType
import org.sui.lang.core.psi.*
import org.sui.lang.core.psi.ext.hasAncestor
import org.sui.lang.core.psi.ext.isMsl
import org.sui.lang.core.psi.ext.structItem
import org.sui.lang.core.types.infer.TypeError
import org.sui.lang.core.types.infer.inference

class MvTypeCheckInspection : MvLocalInspectionTool() {
    override fun buildMvVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : MvVisitor() {
            override fun visitItemSpec(o: MvItemSpec) {
                val inference = o.inference(true)
                inference.typeErrors
                    .forEach {
                        holder.registerTypeError(it)
                    }
            }

            override fun visitModuleItemSpec(o: MvModuleItemSpec) {
                val inference = o.inference(true)
                inference.typeErrors
                    .forEach {
                        holder.registerTypeError(it)
                    }
            }

            override fun visitFunction(o: MvFunction) {
                val inference = o.inference(o.isMsl())
                inference.typeErrors
                    .forEach { typeError ->
                        if (typeError.element.hasAncestor<MvAssertBangExpr>()) return@forEach
                        holder.registerTypeError(typeError)
                    }
            }

            override fun visitSpecFunction(o: MvSpecFunction) {
                val inference = o.inference(true)
                inference.typeErrors
                    .forEach {
                        holder.registerTypeError(it)
                    }
            }

            override fun visitSpecInlineFunction(o: MvSpecInlineFunction) {
                val inference = o.inference(true)
                inference.typeErrors
                    .forEach {
                        holder.registerTypeError(it)
                    }
            }

            override fun visitSchema(o: MvSchema) {
                val inference = o.inference(true)
                inference.typeErrors
                    .forEach {
                        holder.registerTypeError(it)
                    }
            }

            override fun visitNamedFieldDecl(field: MvNamedFieldDecl) {
                val structItem = field.structItem
                for (innerType in field.type?.descendantsOfType<MvPathType>().orEmpty()) {
                    val typeItem = innerType.path.reference?.resolve() as? MvStruct ?: continue
                    if (typeItem == structItem) {
                        holder.registerTypeError(TypeError.CircularType(innerType, structItem))
                    }
                }
            }
        }

    fun ProblemsHolder.registerTypeError(typeError: TypeError) {
        this.registerProblem(
            typeError.element,
            typeError.message(),
            ProblemHighlightType.GENERIC_ERROR,
            *(listOfNotNull(typeError.fix()).toTypedArray())
        )
    }
}
