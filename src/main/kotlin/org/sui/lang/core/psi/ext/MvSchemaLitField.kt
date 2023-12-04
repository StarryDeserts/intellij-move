package org.sui.lang.core.psi.ext

import com.intellij.lang.ASTNode
import org.sui.lang.MvElementTypes
import org.sui.lang.core.psi.*
import org.sui.lang.core.resolve.ref.MvPolyVariantReference
import org.sui.lang.core.resolve.ref.MvReferenceCached
import org.sui.lang.core.resolve.ref.Namespace
import org.sui.lang.core.resolve.resolveLocalItem

val MvSchemaLitField.isShorthand get() = !hasChild(MvElementTypes.COLON)

val MvSchemaLitField.schemaLit: MvSchemaLit? get() = ancestorStrict(stopAt = MvSpecCodeBlock::class.java)

inline fun <reified T : MvElement> MvSchemaLitField.resolveToElement(): T? =
    reference.multiResolve().filterIsInstance<T>().singleOrNull()

fun MvSchemaLitField.resolveToDeclaration(): MvSchemaFieldStmt? = resolveToElement()
fun MvSchemaLitField.resolveToBinding(): MvBindingPat? = resolveToElement()

class MvSchemaFieldReferenceImpl(
    element: MvSchemaLitField
) : MvReferenceCached<MvSchemaLitField>(element) {
    override fun resolveInner(): List<MvNamedElement> {
        return resolveLocalItem(element, setOf(Namespace.SCHEMA_FIELD))
    }
}

class MvSchemaFieldShorthandReferenceImpl(
    element: MvSchemaLitField
) : MvReferenceCached<MvSchemaLitField>(element) {
    override fun resolveInner(): List<MvNamedElement> {
        return listOf(
            resolveLocalItem(element, setOf(Namespace.SCHEMA_FIELD)),
            resolveLocalItem(element, setOf(Namespace.NAME))
        ).flatten()
    }
}

abstract class MvSchemaLitFieldMixin(node: ASTNode) : MvElementImpl(node),
                                                      MvSchemaLitField {
    override fun getReference(): MvPolyVariantReference {
        if (this.isShorthand) {
            return MvSchemaFieldShorthandReferenceImpl(this)
        } else {
            return MvSchemaFieldReferenceImpl(this)
        }
    }
}
