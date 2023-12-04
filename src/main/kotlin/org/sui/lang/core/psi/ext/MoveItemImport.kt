package org.sui.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.sui.lang.core.psi.*
import org.sui.lang.core.psi.impl.MvNamedElementImpl
import org.sui.lang.core.resolve.ItemVis
import org.sui.lang.core.resolve.MslLetScope
import org.sui.lang.core.resolve.ref.MvReferenceCached
import org.sui.lang.core.resolve.ref.Namespace
import org.sui.lang.core.resolve.ref.Visibility
import org.sui.lang.core.resolve.resolveModuleItem

//import org.sui.lang.core.psi.*

fun MvUseItem.useSpeck(): MvItemUseSpeck =
    ancestorStrict() ?: error("ItemImport outside ModuleItemsImport")

val MvUseItem.annotationItem: MvElement
    get() {
        val parent = this.parent
        if (parent is MvUseItemGroup && parent.useItemList.size != 1) return this
        return useStmt
    }

val MvUseItem.useStmt: MvUseStmt
    get() =
        ancestorStrict() ?: error("always has MvUseStmt as ancestor")

val MvUseItem.nameOrAlias: String?
    get() {
        val alias = this.useAlias
        if (alias != null) {
            return alias.identifier?.text
        }
        return this.identifier.text
    }

val MvUseItem.moduleName: String
    get() {
        val useStmt = this.ancestorStrict<MvUseStmt>()
        return useStmt?.itemUseSpeck?.fqModuleRef?.referenceName.orEmpty()
    }

val MvUseItem.isSelf: Boolean get() = this.identifier.textMatches("Self")

class MvUseItemReferenceElement(element: MvUseItem) : MvReferenceCached<MvUseItem>(element) {

    override fun resolveInner(): List<MvNamedElement> {
        val moduleRef = element.useSpeck().fqModuleRef
        val module =
            moduleRef.reference?.resolve() as? MvModule ?: return emptyList()
        if ((element.useAlias == null && element.text == "Self")
            || (element.useAlias != null && element.text.startsWith("Self as"))
        ) return listOf(module)

        val ns = setOf(
            Namespace.TYPE,
            Namespace.NAME,
            Namespace.FUNCTION,
            Namespace.SCHEMA,
            Namespace.ERROR_CONST
        )
        val vs = Visibility.buildSetOfVisibilities(moduleRef)
        val itemVis = ItemVis(
            ns,
            vs,
            MslLetScope.EXPR_STMT,
            itemScope = moduleRef.itemScope,
        )
        return resolveModuleItem(
            module,
            element.referenceName,
            itemVis
        )
    }

}

abstract class MvUseItemMixin(node: ASTNode) : MvNamedElementImpl(node),
    MvUseItem {
    override fun getName(): String? {
        val name = super.getName()
        if (name != "Self") return name
        return ancestorStrict<MvItemUseSpeck>()?.fqModuleRef?.referenceName ?: name
    }

    override val referenceNameElement: PsiElement get() = identifier

    override fun getReference() = MvUseItemReferenceElement(this)
}
