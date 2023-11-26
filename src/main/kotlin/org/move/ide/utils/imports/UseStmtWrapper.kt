/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.move.ide.utils.imports

import org.move.lang.core.psi.MvAddressRef
import org.move.lang.core.psi.MvUseItem
import org.move.lang.core.psi.MvUseStmt
import org.move.lang.core.psi.ext.*

class UseStmtWrapper(val useStmt: MvUseStmt) : Comparable<UseStmtWrapper> {
    private val addr: MvAddressRef? get() = this.useStmt.addressRef

    // `use` order:
    // 1. Standard library (stdlib)
    // 2. Related third party (extern crate)
    // 3. Local
    //    - otherwise
    //    - crate::
    //    - super::
    //    - self::
    val packageGroupLevel: Int = when {
        this.useStmt.isTestOnly -> 5
        this.useStmt.isVerifyOnly -> 6
        else -> this.addr?.useGroupLevel ?: -1
    }

    val addressLevel: Int = when (this.addr?.namedAddress?.referenceName?.lowercase()) {
        "std" -> 0
        "sui" -> 1
        "coin" -> 2
        else -> 3
    }
//    val packageGroupLevel: Int = when {
//
//        basePath?.self != null -> 6
//        basePath?.`super` != null -> 5
//        basePath?.crate != null -> 4
//        else -> when (basePath?.reference?.resolve()?.containingCrate?.origin) {
//            PackageOrigin.WORKSPACE -> 3
//            PackageOrigin.DEPENDENCY -> 2
//            PackageOrigin.STDLIB, PackageOrigin.STDLIB_DEPENDENCY -> 1
//            null -> 3
//        }
//    }

    override fun compareTo(other: UseStmtWrapper): Int =
        compareValuesBy(
            this, other,
            { it.packageGroupLevel }, { it.addressLevel }, { it.useStmt.useSpeckText }
        )
}

val COMPARATOR_FOR_ITEMS_IN_USE_GROUP: Comparator<MvUseItem> =
    compareBy<MvUseItem> { !it.isSelf }.thenBy { it.referenceName.lowercase() }
