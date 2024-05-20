package org.sui.lang.core.psi.ext

import com.intellij.lang.ASTNode
import org.sui.lang.core.completion.getOriginalOrSelf
import org.sui.lang.core.psi.*
import org.sui.lang.core.resolve.ref.MvPolyVariantReference
import org.sui.lang.core.resolve.ref.MvPolyVariantReferenceCached
import org.sui.lang.core.types.infer.inferReceiverTy
import org.sui.lang.core.types.ty.TyStruct

val MvStructDotField.receiverItem: MvStruct?
    get() {
        val dotExpr =
            (this.parent as? MvDotExpr)?.getOriginalOrSelf() ?: return null
        val msl = dotExpr.isMsl()
        val innerTy = dotExpr.inferReceiverTy(msl)
        if (innerTy !is TyStruct) return null
        val structItem = innerTy.item
        if (!msl) {
            // cannot resolve field if not in the same module as struct definition
            val dotExprModule = dotExpr.namespaceModule ?: return null
            if (structItem.containingModule != dotExprModule) return null
        }
        return structItem
    }

class MvStructDotFieldReferenceImpl(
    element: MvStructDotField
) : MvPolyVariantReferenceCached<MvStructDotField>(element) {

    override fun multiResolveInner(): List<MvNamedElement> {
        val receiverItem = element.receiverItem ?: return emptyList()

        val referenceName = element.referenceName
        return receiverItem.fields
            .filter { it.name == referenceName }
    }
}

abstract class MvStructDotFieldMixin(node: ASTNode) : MvElementImpl(node),
    MvStructDotField {
    override fun getReference(): MvPolyVariantReference {
        return MvStructDotFieldReferenceImpl(this)
    }
}
