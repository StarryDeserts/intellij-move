package org.sui.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.sui.lang.core.psi.MvAddressDef
import org.sui.lang.core.psi.MvModuleBlock
import org.sui.lang.core.psi.ext.ancestorStrict
import org.sui.lang.core.psi.ext.hasAncestorOrSelf
import org.sui.lang.core.psi.ext.modules
import org.sui.lang.core.psi.psiFactory

class InlineAddressBlockIntention : MvElementBaseIntentionAction<InlineAddressBlockIntention.Context>() {

    override fun getText(): String = "Inline address block"

    override fun getFamilyName(): String = text

    data class Context(val address: MvAddressDef)

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        if (element.hasAncestorOrSelf<MvModuleBlock>()) return null
        val address = element.ancestorStrict<MvAddressDef>() ?: return null
        if (address.modules().size != 1) return null
        return Context(address)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val address = ctx.address
        val addressText = address.addressRef?.text ?: return
        val module = address.modules().firstOrNull() ?: return
        val moduleNameElement = module.nameElement ?: return
        val blockText = module.moduleBlock?.text ?: return

//        val nameOffset = moduleNameElement.startOffset
//        var caretOffset = editor.caretModel.offset
//        if (caretOffset > nameOffset) {
//            // focused on identifier
//           caretOffset += addressText.length + 2
//        }

        val inlineModule = project.psiFactory.inlineModule(addressText, moduleNameElement.text, blockText)
        address.replace(inlineModule)

//        editor.caretModel.moveToOffset(caretOffset)
    }
}
