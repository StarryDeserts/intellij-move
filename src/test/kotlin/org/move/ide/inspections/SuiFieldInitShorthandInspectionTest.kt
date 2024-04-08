package org.sui.ide.inspections

import org.sui.utils.tests.annotation.InspectionTestBase

class SuiFieldInitShorthandInspectionTest : InspectionTestBase(FieldInitShorthandInspection::class) {

    fun `test not applicable`() = checkFixIsUnavailable(
        "Use initialization shorthand", """
    module 0x1::M {
        fun m() {
            let _ = S { foo: bar/*caret*/, baz: &baz };
        }
    }    
    """, checkWeakWarn = true
    )

    fun `test fix for struct literal`() = checkFixByText(
        "Use initialization shorthand", """
    module 0x1::M {
        fun m() {
            let _ = S { <weak_warning descr="Expression can be simplified">foo: foo/*caret*/</weak_warning>, baz: quux };
        }
    }    
    """, """
    module 0x1::M {
        fun m() {
            let _ = S { foo/*caret*/, baz: quux };
        }
    }    
    """
    )

    fun `test fix for struct pattern`() = checkFixByText(
        "Use pattern shorthand", """
    module 0x1::M {
        struct S { foo: u8 }
        fun m() {
            let foo = 1;
            let S { <weak_warning descr="Expression can be simplified">foo: foo/*caret*/</weak_warning> } = call();
        }
    }    
    """, """
    module 0x1::M {
        struct S { foo: u8 }
        fun m() {
            let foo = 1;
            let S { foo } = call();
        }
    }    
    """
    )

    fun `test fix for schema literal`() = checkFixByText(
        "Use initialization shorthand", """
    module 0x1::M {
        spec module {
            include Schema { <weak_warning descr="Expression can be simplified">foo: foo/*caret*/</weak_warning> };
        }
    }    
    """, """
    module 0x1::M {
        spec module {
            include Schema { foo };
        }
    }    
    """
    )
}
