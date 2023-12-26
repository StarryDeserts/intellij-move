package org.sui.lang.core.resolve.ref

import org.sui.lang.core.psi.*
import org.sui.lang.core.psi.ext.isSelf
import org.sui.lang.core.psi.ext.useSpeck
import org.sui.lang.core.resolve.resolveSingleItem
import org.sui.stdext.wrapWithList

class MvModuleReferenceImpl(
    element: MvModuleRef,
) : MvReferenceCached<MvModuleRef>(element) {

    override fun resolveInner(): List<MvNamedElement> {
        if (element.isSelf) return element.containingModule.wrapWithList()

        val resolved = resolveSingleItem(element, setOf(Namespace.MODULE))
        if (resolved is MvUseAlias) {
            return resolved.wrapWithList()
        }
        val moduleRef = when {
            resolved is MvUseItem && resolved.text == "Self" -> resolved.useSpeck().fqModuleRef
            resolved is MvModuleUseSpeck -> resolved.fqModuleRef
            else -> return emptyList()
        }
        return moduleRef?.reference?.resolve().wrapWithList()
    }
}
