package org.sui.lang.core.psi.ext

import com.intellij.lang.ASTNode
import org.sui.lang.core.psi.*
import org.sui.lang.core.resolve.ScopeItem
import org.sui.lang.core.resolve.ref.MvPolyVariantReference
import org.sui.lang.core.resolve.ref.MvPolyVariantReferenceBase
import org.sui.lang.core.types.infer.inference
import org.sui.lang.core.types.ty.TyStruct
import org.sui.stdext.wrapWithList

fun getFieldVariants(element: MvMethodOrField, receiverTy: TyStruct, msl: Boolean): MatchSequence<MvStructField> {
    val structItem = receiverTy.item
        if (!msl) {
            // cannot resolve field if not in the same module as struct definition
            val dotExprModule = element.namespaceModule ?: return emptySequence()
            if (structItem.containingModule != dotExprModule) return emptySequence()
        }
    return structItem.fields
        .map { ScopeItem(it.name, it) }.asSequence()
    }

class MvStructDotFieldReferenceImpl(
    element: MvStructDotField
) : MvPolyVariantReferenceBase<MvStructDotField>(element) {

    override fun multiResolve(): List<MvNamedElement> {
        val msl = element.isMsl()
        val receiverExpr = element.receiverExpr
        val inference = receiverExpr.inference(msl) ?: return emptyList()
        return inference.getResolvedField(element).wrapWithList()
    }
}

abstract class MvStructDotFieldMixin(node: ASTNode) : MvElementImpl(node),
    MvStructDotField {
    override fun getReference(): MvPolyVariantReference {
        return MvStructDotFieldReferenceImpl(this)
    }
}
