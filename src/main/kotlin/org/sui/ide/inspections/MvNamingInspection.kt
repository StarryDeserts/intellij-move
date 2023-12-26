package org.sui.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.sui.lang.core.psi.*

abstract class MvNamingInspection(private val elementTitle: String) : MvLocalInspectionTool() {

    override fun getDisplayName() = "$elementTitle naming convention"

    override val isSyntaxOnly: Boolean = true
}

class SuiMvConstNamingInspection : MvNamingInspection("Constant") {
    override fun buildMvVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : MvVisitor() {
        override fun visitConst(o: MvConst) {
            val ident = o.identifier ?: return
            val name = ident.text
            if (!name.startsWithUpperCaseLetter()) {
                holder.registerProblem(
                    ident,
                    "Invalid constant name `$name`. Constant names must start with 'A'..'Z'"
                )
            }
        }
    }
}

class SuiMvFunctionNamingInspection : MvNamingInspection("Function") {
    override fun buildMvVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : MvVisitor() {

        override fun visitFunction(o: MvFunction) = checkFunctionName(o)

        override fun visitSpecFunction(o: MvSpecFunction) = checkFunctionName(o)

        private fun checkFunctionName(o: MvFunctionLike) {
            val ident = o.nameIdentifier ?: return
            val name = ident.text
            if (name.startsWithUnderscore()) {
                holder.registerProblem(
                    ident,
                    "Invalid function name '$name'. Function names cannot start with '_'"
                )
            }
        }
    }
}

class SuiMvStructNamingInspection : MvNamingInspection("Struct") {
    override fun buildMvVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : MvVisitor() {
        override fun visitStruct(o: MvStruct) {
            val ident = o.identifier ?: return
            val name = ident.text
            if (!name.startsWithUpperCaseLetter()) {
                holder.registerProblem(
                    ident,
                    "Invalid struct name `$name`. Struct names must start with 'A'..'Z'"
                )
            }
        }
    }
}

class SuiMvLocalBindingNamingInspection : MvNamingInspection("Local variable") {
    override fun buildMvVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : MvVisitor() {
        override fun visitBindingPat(o: MvBindingPat) {
            // filter out constants
            if (o.parent is MvConst) return

            val ident = o.identifier
            val name = ident.text
            val trimmed = name.trimStart('_')
            if (trimmed.isNotBlank() && !trimmed.startsWithLowerCaseLetter()) {
                holder.registerProblem(
                    ident,
                    "Invalid local variable name `$name`. Local variable names must start with 'a'..'z'"
                )
            }
        }
    }
}

fun String.startsWithUpperCaseLetter(): Boolean = this[0].isUpperCase()

fun String.startsWithLowerCaseLetter(): Boolean = this[0].isLowerCase()

fun String.startsWithUnderscore(): Boolean = this[0] == '_'
