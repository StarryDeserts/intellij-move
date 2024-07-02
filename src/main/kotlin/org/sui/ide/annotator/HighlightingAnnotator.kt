package org.sui.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.sui.ide.colors.MvColor
import org.sui.lang.MvElementTypes.HEX_INTEGER_LITERAL
import org.sui.lang.MvElementTypes.IDENTIFIER
import org.sui.lang.core.psi.*
import org.sui.lang.core.psi.ext.*
import org.sui.lang.core.types.infer.inference
import org.sui.lang.core.types.ty.Ty
import org.sui.lang.core.types.ty.TyReference
import org.sui.lang.core.types.ty.TyStruct

val INTEGER_TYPE_IDENTIFIERS = setOf("u8", "u16", "u32", "u64", "u128", "u256")
val SPEC_INTEGER_TYPE_IDENTIFIERS = INTEGER_TYPE_IDENTIFIERS + setOf("num")
val SPEC_ONLY_PRIMITIVE_TYPES = setOf("num")
val PRIMITIVE_TYPE_IDENTIFIERS = INTEGER_TYPE_IDENTIFIERS + setOf("bool")
val PRIMITIVE_BUILTIN_TYPE_IDENTIFIERS = setOf("address", "signer")

val GLOBAL_STORAGE_ACCESS_FUNCTIONS =
    setOf("move_from", "borrow_global", "borrow_global_mut", "exists", "freeze")
val BUILTIN_FUNCTIONS =
    GLOBAL_STORAGE_ACCESS_FUNCTIONS + setOf("move_to")
val SPEC_BUILTIN_FUNCTIONS = setOf(
    "global", "len", "vec", "concat", "contains", "index_of", "range",
    "in_range", "update", "update_field", "old", "TRACE", "int2bv", "bv2int"
)

val BUILTIN_TYPE_IDENTIFIERS =
    PRIMITIVE_BUILTIN_TYPE_IDENTIFIERS + setOf(
        "transfer", "object", "tx_context", "vector", "option",
        "UID", "ID", "Option", "TxContext"
    )
val HAS_DROP_ABILITY_TYPES = INTEGER_TYPE_IDENTIFIERS + PRIMITIVE_BUILTIN_TYPE_IDENTIFIERS + setOf(
    "address", "signer", "vector", "Option", "String", "TypeName"
)

val PRELOAD_STD_MODULES = setOf("vector", "option")
val PRELOAD_SUI_MODULES = setOf("transfer", "object", "tx_context")
val PRELOAD_MODULE_ITEMS = setOf("UID", "ID", "TxContext")

class HighlightingAnnotator : MvAnnotatorBase() {
    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        val color = when {
            element is LeafPsiElement -> highlightLeaf(element)
            element is MvLitExpr && element.text.startsWith("@") -> MvColor.ADDRESS
            else -> null
        } ?: return
        val severity = color.testSeverity
        holder.newSilentAnnotation(severity)
            .textAttributes(color.textAttributesKey)
            .create()
    }

    private fun highlightLeaf(element: PsiElement): MvColor? {
        val parent = element.parent as? MvElement ?: return null
        val leafType = element.elementType
        if (leafType.toString().endsWith("_kw")) return MvColor.KEYWORD
        return when {
            leafType == IDENTIFIER -> highlightIdentifier(parent)
            leafType == HEX_INTEGER_LITERAL -> MvColor.NUMBER
            parent is MvAssertBangExpr -> MvColor.MACRO
            parent is MvCopyExpr
                    && element.text == "copy" -> MvColor.KEYWORD
            else -> null
        }
    }

    private fun highlightIdentifier(element: MvElement): MvColor? {
        if (element is MvAssertBangExpr) return MvColor.MACRO
        if (element is MvAbility) return MvColor.ABILITY
        if (element is MvTypeParameter) return MvColor.TYPE_PARAMETER
        if (element is MvItemSpecTypeParameter) return MvColor.TYPE_PARAMETER
        if (element is MvModuleRef && element.isSelf) return MvColor.KEYWORD
        if (element is MvUseItem && element.text == "Self") return MvColor.KEYWORD
        if (element is MvFunction)
            return when {
                element.isInline -> MvColor.INLINE_FUNCTION
                element.isView -> MvColor.VIEW_FUNCTION
                element.isEntry -> MvColor.ENTRY_FUNCTION
                else -> MvColor.FUNCTION
            }
        if (element is MvStruct) return MvColor.STRUCT
        if (element is MvStructField) return MvColor.FIELD
        if (element is MvStructDotField) return MvColor.FIELD
        if (element is MvStructPatField) return MvColor.FIELD
        if (element is MvStructLitField) return MvColor.FIELD
        if (element is MvConst) return MvColor.CONSTANT
        if (element is MvModule) return MvColor.MODULE
        if (element is MvVectorLitExpr) return MvColor.VECTOR_LITERAL

        return when (element) {
            is MvPath -> highlightPathElement(element)
            is MvBindingPat -> highlightBindingPat(element)
            else -> null
        }
    }

    private fun highlightBindingPat(bindingPat: MvBindingPat): MvColor {
//        val msl = bindingPat.isMslLegacy()
        val msl = bindingPat.isMslOnlyItem
        val itemTy = bindingPat.inference(msl)?.getPatType(bindingPat)
        return if (itemTy != null) {
            highlightVariableByType(itemTy)
        } else {
            MvColor.VARIABLE
        }
    }

    private fun highlightPathElement(path: MvPath): MvColor? {
        // any qual :: access is not highlighted
        if (path.isQualPath) return null

        val identifierName = path.identifierName
        val pathOwner = path.parent
        return when (pathOwner) {
            is MvPathType -> {
                when {
                    identifierName in PRIMITIVE_TYPE_IDENTIFIERS -> MvColor.PRIMITIVE_TYPE
                    identifierName in SPEC_ONLY_PRIMITIVE_TYPES && path.isMslScope -> MvColor.PRIMITIVE_TYPE
                    identifierName in BUILTIN_TYPE_IDENTIFIERS -> MvColor.BUILTIN_TYPE
                    else -> {
                        val item = path.reference?.resolve()
                        when (item) {
                            is MvTypeParameter -> MvColor.TYPE_PARAMETER
                            is MvStruct -> MvColor.STRUCT
                            else -> null
                        }
                    }
                }
            }
            is MvCallExpr -> {
                val item = path.reference?.resolveWithAliases()
                when {
                    item is MvSpecFunction
                            && item.isNative
                            && identifierName in SPEC_BUILTIN_FUNCTIONS -> MvColor.BUILTIN_FUNCTION_CALL
                    item is MvFunction
                            && item.isNative
                            && identifierName in BUILTIN_FUNCTIONS -> MvColor.BUILTIN_FUNCTION_CALL
                    item is MvFunction && item.isEntry -> MvColor.ENTRY_FUNCTION_CALL
                    item is MvFunction && item.isView -> MvColor.VIEW_FUNCTION_CALL
                    item is MvFunction && item.isInline -> MvColor.INLINE_FUNCTION_CALL
                    else -> MvColor.FUNCTION_CALL
                }
            }
            is MvStructLitExpr -> MvColor.STRUCT
            is MvStructPat -> MvColor.STRUCT
            is MvRefExpr -> {
                val item = path.reference?.resolve() ?: return null
                if (item is MvConst) {
                    MvColor.CONSTANT
                } else {
                    val msl = path.isMslScope
                    val itemTy = pathOwner.inference(msl)?.getExprType(pathOwner)
                    if (itemTy != null) {
                        highlightVariableByType(itemTy)
                    } else {
                        MvColor.VARIABLE
                    }
                }
            }
            else -> null
        }
    }

    private fun highlightVariableByType(itemTy: Ty): MvColor =
        when {
            itemTy is TyReference -> {
                val referenced = itemTy.referenced
                when {
                    referenced is TyStruct && referenced.item.hasKey ->
                        if (itemTy.isMut) MvColor.MUT_REF_TO_KEY_OBJECT else MvColor.REF_TO_KEY_OBJECT
                    referenced is TyStruct && referenced.item.hasStore && !referenced.item.hasDrop ->
                        if (itemTy.isMut) MvColor.MUT_REF_TO_STORE_NO_DROP_OBJECT else MvColor.REF_TO_STORE_NO_DROP_OBJECT
                    referenced is TyStruct && referenced.item.hasStore && referenced.item.hasDrop ->
                        if (itemTy.isMut) MvColor.MUT_REF_TO_STORE_OBJECT else MvColor.REF_TO_STORE_OBJECT
                    else ->
                        if (itemTy.isMut) MvColor.MUT_REF else MvColor.REF
                }
            }
            itemTy is TyStruct && itemTy.item.hasStore && !itemTy.item.hasDrop -> MvColor.STORE_NO_DROP_OBJECT
            itemTy is TyStruct && itemTy.item.hasStore -> MvColor.STORE_OBJECT
            itemTy is TyStruct && itemTy.item.hasKey -> MvColor.KEY_OBJECT
            else -> MvColor.VARIABLE
        }
}
