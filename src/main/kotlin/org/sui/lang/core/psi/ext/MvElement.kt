package org.sui.lang.core.psi.ext

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import org.sui.cli.MoveProject
import org.sui.cli.moveProjects
import org.sui.lang.core.psi.*
import org.sui.lang.moveProject
import org.sui.lang.toNioPathOrNull

fun MvElement.isInsideAssignmentLhs(): Boolean {
    val parent = PsiTreeUtil.findFirstParent(this, false) {
        it is MvAssignmentExpr || it is MvInitializer
    }
    return parent is MvAssignmentExpr
}

fun PsiFileSystemItem.findMoveProject(): MoveProject? {
    if (this is org.sui.lang.MoveFile) return this.moveProject
    val path = virtualFile.toNioPathOrNull() ?: return null
    return project.moveProjects.findMoveProject(path)
}

//private val MSL_KEY: Key<CachedValue<FunctionSignature?>> = Key.create("SIGNATURE_KEY")

fun PsiElement.isMsl(): Boolean {
    return CachedValuesManager.getProjectPsiDependentCache(this) {
        var element: PsiElement? = it
        while (element != null) {
            // use items always non-msl, otherwise import resolution doesn't work correctly
            if (element is MvUseItem) return@getProjectPsiDependentCache false

            // module items
            if (element is MvModule
                || element is MvFunction
                || element is MvStruct
            )
                return@getProjectPsiDependentCache false

            if (element is MslScopeElement) return@getProjectPsiDependentCache true

            element = element.parent as? MvElement
        }
        false
    }
}
