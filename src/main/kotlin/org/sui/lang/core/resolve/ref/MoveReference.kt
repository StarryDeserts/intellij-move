package org.sui.lang.core.resolve.ref

import com.intellij.psi.PsiPolyVariantReference
import org.sui.lang.core.psi.MvElement
import org.sui.lang.core.psi.MvNamedElement
import org.sui.lang.core.psi.MvUseAlias
import org.sui.lang.core.psi.ext.parentUseSpeck

interface MvPolyVariantReference : PsiPolyVariantReference {

    override fun getElement(): MvElement

    override fun resolve(): MvNamedElement?

    fun resolveFollowingAliases(): MvNamedElement? {
        val resolved = this.resolve()
        if (resolved is MvUseAlias) {
            val aliasedPath = resolved.parentUseSpeck.path
            return aliasedPath.reference?.resolve()
        }
        return resolved
    }

    fun multiResolve(): List<MvNamedElement>
}

interface MvPathReference : MvPolyVariantReference {

//    fun multiResolveIfVisible(): List<MvElement> = multiResolve()
//
//    fun rawMultiResolve(): List<RsPathResolveResult<MvElement>> =
//        multiResolve().map { RsPathResolveResult(it, isVisible = true) }
}

interface MvPath2Reference : MvPolyVariantReference {
//    fun multiResolveIfVisible(): List<MvElement> = multiResolve()

//    fun rawMultiResolve(): List<RsPathResolveResult<MvElement>>
//        multiResolve().map { RsPathResolveResult(it, isVisible = true) }
    
}
