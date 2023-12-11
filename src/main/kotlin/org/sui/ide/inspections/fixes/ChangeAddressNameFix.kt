package org.sui.ide.inspections.fixes

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.sui.ide.inspections.DiagnosticFix
import org.sui.lang.core.psi.MvFQModuleRef
import org.sui.lang.core.psi.MvModule
import org.sui.lang.core.psi.psiFactory
import org.sui.lang.core.types.address
import org.sui.lang.moveProject

class ChangeAddressNameFix(
    moduleRef: MvFQModuleRef,
    val newAddressRefName: String
) : DiagnosticFix<MvFQModuleRef>(moduleRef) {

    override fun getText(): String = "Change address to `$newAddressRefName`"
    override fun getFamilyName(): String = "Change address ref"

    override fun stillApplicable(project: Project, file: PsiFile, element: MvFQModuleRef): Boolean {
        return element.addressRef.namedAddress?.referenceName != newAddressRefName
    }

    override fun invoke(project: Project, file: PsiFile, element: MvFQModuleRef) {
        val ref = element

        // resolve by value
        val mod = ref.reference?.resolve() as? MvModule ?: return
        val proj = mod.moveProject ?: return

        val modAddressRef = mod.addressRef ?: return
        if (ref.addressRef.address(proj) != mod.address(proj)) {
            val newAddressRef = project.psiFactory.addressRef(modAddressRef.text)
            ref.addressRef.replace(newAddressRef)
        }
    }
}
