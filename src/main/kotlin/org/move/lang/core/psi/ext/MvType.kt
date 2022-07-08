package org.move.lang.core.psi.ext

import org.move.lang.core.psi.MvPathType
import org.move.lang.core.psi.MvRefType
import org.move.lang.core.psi.MvType
import org.move.lang.core.psi.MvTypeArgument
import org.move.lang.core.resolve.ref.MvReference
import org.move.lang.core.types.infer.inferMvTypeTy
import org.move.lang.core.types.ty.Ty

fun MvType.ty(msl: Boolean = this.isMsl()): Ty = inferMvTypeTy(this, msl)

val MvType.moveReference: MvReference?
    get() = when (this) {
        is MvPathType -> this.path.reference
        is MvRefType -> this.type?.moveReference
        else -> null
    }
val MvType.typeArguments: List<MvTypeArgument>
    get() {
        return when (this) {
            is MvPathType -> this.path.typeArguments
            is MvRefType -> this.type?.typeArguments.orEmpty()
            else -> emptyList()
        }
    }