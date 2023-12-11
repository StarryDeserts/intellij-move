package org.sui.lang.core.psi.ext

import org.sui.lang.core.psi.*
import org.sui.stdext.wrapWithList

val MvUseStmt.addressRef: MvAddressRef?
    get() {
        val moduleUseSpeck = this.moduleUseSpeck
        if (moduleUseSpeck != null) {
            val fqModuleRef = moduleUseSpeck.fqModuleRef
            if (fqModuleRef != null) {
                return fqModuleRef.addressRef
            } else {
                return moduleUseSpeck.addressRef
            }
        }
        val itemUseSpeck = this.itemUseSpeck
        if (itemUseSpeck != null) {
            return itemUseSpeck.fqModuleRef.addressRef
        }
        return null
    }

val MvUseStmt.useGroupLevel: Int
    get() {
        if (this.isTestOnly) return 5
        return this.addressRef?.useGroupLevel ?: -1
    }

val MvUseStmt.fqModuleText: String?
    get() {
        val fqModuleRef = this.fqModuleRef ?: return null
        return fqModuleRef.text
    }

val MvUseStmt.fqModuleRef: MvFQModuleRef?
    get() {
        val moduleUseSpeck = this.moduleUseSpeck
        if (moduleUseSpeck != null) {
            return moduleUseSpeck.fqModuleRef
        }
        val itemUseSpeck = this.itemUseSpeck
        if (itemUseSpeck != null) {
            return itemUseSpeck.fqModuleRef
        }
        return null
    }

val MvUseStmt.childUseItems: List<MvUseItem>
    get() {
        val itemUseSpeck = this.itemUseSpeck
        if (itemUseSpeck != null) {
            val group = itemUseSpeck.useItemGroup
            if (group != null) {
                return group.useItemList
            }
            return itemUseSpeck.useItem.wrapWithList()
        }
        return emptyList()
    }

val MvUseStmt.useSpeck: MvUseSpeck? get() = this.itemUseSpeck ?: this.moduleUseSpeck

val MvUseStmt.useSpeckText: String
    get() {
        val moduleUseSpeck = this.moduleUseSpeck
        if (moduleUseSpeck != null) {
            return moduleUseSpeck.text
        }
        val itemUseSpeck = this.itemUseSpeck
        if (itemUseSpeck != null) {
            return itemUseSpeck.text
        }
        return ""
    }
