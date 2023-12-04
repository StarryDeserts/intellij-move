package org.sui.lang.core.psi.ext

import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import org.sui.ide.MoveIcons
import org.sui.lang.core.psi.MvStruct
import org.sui.lang.core.psi.MvStructBlock
import org.sui.lang.core.psi.MvStructField
import org.sui.lang.core.psi.impl.MvMandatoryNameIdentifierOwnerImpl
import javax.swing.Icon

val MvStructField.fieldsDefBlock: MvStructBlock?
    get() =
        parent as? MvStructBlock

val MvStructField.structItem: MvStruct
    get() =
        fieldsDefBlock?.parent as MvStruct

abstract class MvStructFieldMixin(node: ASTNode) : MvMandatoryNameIdentifierOwnerImpl(node),
                                                   MvStructField {

    override fun getIcon(flags: Int): Icon = MoveIcons.STRUCT_FIELD

    override fun getPresentation(): ItemPresentation {
        val fieldType = this.typeAnnotation?.text ?: ""
        return PresentationData(
            "${this.name}${fieldType}",
            null,
            MoveIcons.STRUCT_FIELD,
            null
        )
    }
}