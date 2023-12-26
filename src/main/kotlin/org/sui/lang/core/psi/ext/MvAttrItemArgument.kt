package org.sui.lang.core.psi.ext

import com.intellij.lang.ASTNode
import org.sui.lang.core.psi.*
import org.sui.lang.core.resolve.ref.MvPolyVariantReference
import org.sui.lang.core.resolve.ref.MvReferenceCached

class AttrItemArgumentReferenceImpl(
    element: MvAttrItemArgument,
    val ownerFunction: MvFunction
) : MvReferenceCached<MvAttrItemArgument>(element) {

    override fun resolveInner(): List<MvNamedElement> {
        return ownerFunction.parameters
            .map { it.bindingPat }
            .filter { it.name == element.referenceName }
    }
}

abstract class MvAttrItemArgumentMixin(node: ASTNode) : MvElementImpl(node),
                                                        MvAttrItemArgument {

    override fun getReference(): MvPolyVariantReference? {
        val attr = this.ancestorStrict<MvAttr>() ?: return null
        attr.attrItemList
            .singleOrNull()
            ?.takeIf { it.identifier.text == "test" } ?: return null
        val ownerFunction = attr.owner as? MvFunction ?: return null
        return AttrItemArgumentReferenceImpl(this, ownerFunction)
    }
}
