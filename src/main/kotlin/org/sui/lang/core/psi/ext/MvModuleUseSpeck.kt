package org.sui.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.sui.ide.MoveIcons
import org.sui.lang.core.psi.MvModuleUseSpeck
import org.sui.lang.core.psi.impl.MvNamedElementImpl
import javax.swing.Icon

abstract class MvModuleUseSpeckMixin(node: ASTNode) : MvNamedElementImpl(node),
    MvModuleUseSpeck {
    override val nameElement: PsiElement?
        get() =
            useAlias?.identifier ?: fqModuleRef?.identifier

    override fun getIcon(flags: Int): Icon = MoveIcons.MODULE

//    override fun getReference(): MvReference {
//        return MvModuleReferenceImpl(moduleRef)
//    }

}
