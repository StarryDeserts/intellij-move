package org.sui.lang.core.types.infer

import org.sui.cli.settings.debugErrorOrFallback
import org.sui.ide.annotator.INTEGER_TYPE_IDENTIFIERS
import org.sui.ide.annotator.SPEC_INTEGER_TYPE_IDENTIFIERS
import org.sui.lang.core.psi.*
import org.sui.lang.core.psi.ext.*
import org.sui.lang.core.types.ty.*

fun MvType.loweredType(msl: Boolean): Ty = TyLowering.lowerType(this, msl)

class TyLowering {
    fun lowerTy(moveType: MvType, msl: Boolean): Ty {
        return when (moveType) {
            is MvPathType -> lowerPath(moveType.path, msl)
            is MvRefType -> {
                val mutabilities = RefPermissions.valueOf(moveType.mutable)
                val refInnerType = moveType.type
                    ?: return TyReference(TyUnknown, mutabilities, msl)
                val innerTy = lowerTy(refInnerType, msl)
                TyReference(innerTy, mutabilities, msl)
            }
            is MvTupleType -> {
                val innerTypes = moveType.typeList.map { lowerTy(it, msl) }
                TyTuple(innerTypes)
            }
            is MvUnitType -> TyUnit
            is MvParensType -> lowerTy(moveType.type, msl)
            is MvLambdaType -> {
                val paramTys = moveType.paramTypes.map { lowerTy(it, msl) }
                val returnType = moveType.returnType
                val retTy = if (returnType == null) {
                    TyUnit
                } else {
                    lowerTy(returnType, msl)
                }
                TyLambda(paramTys, retTy)
            }
            else -> moveType.project.debugErrorOrFallback(
                "${moveType.elementType} type is not inferred",
                TyUnknown
            )
        }
    }

    private fun lowerPath(path: MvPath, msl: Boolean): Ty {
        val namedItem = path.reference?.resolveWithAliases()
        if (namedItem == null) {
            return lowerPrimitiveTy(path, msl)
        }
        return when (namedItem) {
            is MvTypeParameter -> TyTypeParameter(namedItem)
            is MvTypeParametersOwner -> {
                val baseTy = namedItem.declaredType(msl)
                val (_, explicits) = instantiatePathGenerics(path, namedItem, msl)
                baseTy.substitute(explicits)
            }
            else -> namedItem.project.debugErrorOrFallback(
                "${namedItem.elementType} path cannot be inferred into type",
                TyUnknown
            )
        }
    }

    private fun lowerPrimitiveTy(path: MvPath, msl: Boolean): Ty {
        val refName = path.referenceName ?: return TyUnknown
        if (msl && refName in SPEC_INTEGER_TYPE_IDENTIFIERS) return TyInteger.fromName("num")
        if (msl && refName == "bv") return TySpecBv

        val ty = when (refName) {
            in INTEGER_TYPE_IDENTIFIERS -> TyInteger.fromName(refName)
            "bool" -> TyBool
            "address" -> TyAddress
            "signer" -> TySigner
            "vector" -> {
                val argType = path.typeArguments.firstOrNull()?.type
                val itemTy = argType?.let { lowerTy(it, msl) } ?: TyUnknown
                return TyVector(itemTy)
            }
            else -> TyUnknown
        }
        return ty
    }

    private fun <T : MvElement> instantiatePathGenerics(
        path: MvPath,
        namedItem: T,
        msl: Boolean
    ): BoundElement<T> {
        if (namedItem !is MvTypeParametersOwner) return BoundElement(namedItem)

        val psiSubstitution = pathPsiSubst(path, namedItem)

        val typeSubst = hashMapOf<TyTypeParameter, Ty>()
        for ((param, value) in psiSubstitution.typeSubst.entries) {
            val paramTy = TyTypeParameter(param)
            val valueTy = when (value) {
                is RsPsiSubstitution.Value.Present -> lowerTy(value.value, msl)
                is RsPsiSubstitution.Value.OptionalAbsent -> paramTy
                is RsPsiSubstitution.Value.RequiredAbsent -> TyUnknown
            }
            typeSubst[paramTy] = valueTy
        }
        val newSubst = Substitution(typeSubst)
        return BoundElement(namedItem, newSubst)
    }

    companion object {
        fun lowerType(type: MvType, msl: Boolean): Ty {
            return TyLowering().lowerTy(type, msl)
        }

        fun lowerPath(path: MvPath, msl: Boolean): Ty {
            return TyLowering().lowerPath(path, msl)
        }
    }
}
