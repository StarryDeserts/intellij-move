package org.sui.lang.core.resolve.ref

import org.sui.lang.core.psi.MvNamedElement
import org.sui.lang.core.psi.MvStruct
import org.sui.lang.core.psi.MvStructLitField
import org.sui.lang.core.psi.MvStructPatField
import org.sui.lang.core.psi.ext.*
import org.sui.lang.core.resolve.resolveLocalItem

interface MvStructRefField : MvReferenceElement

class MvStructRefFieldReferenceImpl(
    element: MvStructRefField
) : MvPolyVariantReferenceCached<MvStructRefField>(element) {

    override fun multiResolveInner() = resolveIntoStructField(element)
}

class MvStructLitShorthandFieldReferenceImpl(
    element: MvStructLitField,
) : MvPolyVariantReferenceCached<MvStructLitField>(element) {

    override fun multiResolveInner(): List<MvNamedElement> {
        return listOf(
            resolveIntoStructField(element),
            resolveLocalItem(element, setOf(Namespace.NAME))
        ).flatten()
    }
}

class MvStructPatShorthandFieldReferenceImpl(
    element: MvStructPatField
) : MvPolyVariantReferenceCached<MvStructPatField>(element) {

    override fun multiResolveInner(): List<MvNamedElement> = resolveIntoStructField(element)
}

private val MvStructRefField.maybeStruct: MvStruct?
    get() {
        return when (this) {
            is MvStructPatField -> this.structPat.structItem
            is MvStructLitField -> this.structLitExpr.path.maybeStruct
            else -> null
        }
    }

private fun resolveIntoStructField(element: MvStructRefField): List<MvNamedElement> {
    val structItem = element.maybeStruct ?: return emptyList()
    val referenceName = element.referenceName
    return structItem.fields
        .filter { it.name == referenceName }
}
