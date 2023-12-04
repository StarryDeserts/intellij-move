package org.move.ide.inspections.fixes

import org.sui.ide.inspections.SuiRedundantTypeCastInspection
import org.sui.utils.tests.annotation.InspectionTestBase

class RemoveRedundantCastFixTest : InspectionTestBase(SuiRedundantTypeCastInspection::class) {
    fun `test remove redundant cast no parens`() = checkFixByText("Remove redundant cast", """
module 0x1::main {
    fun main() {
        1u64 <warning descr="No cast needed">as /*caret*/u64</warning>;
    }
}        
    """, """
module 0x1::main {
    fun main() {
        1u64;
    }
}        
    """)

    fun `test remove redundant cast parens`() = checkFixByText("Remove redundant cast", """
module 0x1::main {
    fun main() {
        (1u64 <warning descr="No cast needed">as /*caret*/u64</warning>);
    }
}        
    """, """
module 0x1::main {
    fun main() {
        1u64;
    }
}        
    """)
}
