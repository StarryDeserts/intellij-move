package org.move.ide.inspections

import org.sui.ide.inspections.SuiInvalidModuleDeclarationInspection
import org.sui.utils.tests.annotation.InspectionTestBase

class SuiInvalidModuleDeclarationInspectionTest : InspectionTestBase(SuiInvalidModuleDeclarationInspection::class) {
    fun `test module in address block - no error`() = checkByText("""
    address 0x1 { module M {} }    
    """)

    fun `test module with inline address - no error`() = checkByText("""
    module 0x1::M {}
    """)

    fun `test error if no address block or address specified`() = checkByText("""
    module <error descr="Invalid module declaration. The module does not have a specified address / address block.">M</error> {}    
    """)
}
