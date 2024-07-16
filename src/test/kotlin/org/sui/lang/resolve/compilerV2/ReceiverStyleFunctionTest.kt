package org.sui.lang.resolve.compilerV2

import org.sui.ide.inspections.fixes.CompilerV2Feat.RECEIVER_STYLE_FUNCTIONS
import org.sui.utils.tests.CompilerV2Features
import org.sui.utils.tests.resolve.ResolveTestCase

@CompilerV2Features(RECEIVER_STYLE_FUNCTIONS)
class ReceiverStyleFunctionTest : ResolveTestCase() {

    fun `test resolve receiver function`() = checkByCode(
        """
        module 0x1::main {
            struct S { x: u64 }
            fun receiver(self: S, y: u64): u64 {
               //X
                self.x + y
            }
            fun test_call_styles(s: S): u64 {
                s.receiver(1)
                  //^
            }
        }        
    """
    )

    fun `test resolve receiver function with reference`() = checkByCode(
        """
        module 0x1::main {
            struct S { x: u64 }
            fun receiver_ref(self: &S, y: u64): u64 {
               //X
                self.x + y
            }
            fun test_call_styles(s: S): u64 {
                s.receiver_ref(1)
                  //^
            }
        }        
    """
    )

    fun `test resolve receiver function with mut reference`() = checkByCode(
        """
        module 0x1::main {
            struct S { x: u64 }
            fun receiver_mut_ref(self: &mut S, y: u64): u64 {
               //X
                self.x + y
            }
            fun test_call_styles(s: S): u64 {
                s.receiver_mut_ref(1)
                  //^
            }
        }        
    """
    )

    fun `test resolve receiver function with inline mut reference`() = checkByCode(
        """
        module 0x1::main {
            struct S { x: u64 }
            inline fun receiver_mut_ref(self: &mut S, y: u64): u64 {
                      //X
                self.x + y
            }
            fun test_call_styles(s: S): u64 {
                s.receiver_mut_ref(1)
                  //^
            }
        }        
    """
    )

    fun `test cannot be resolved if self has another type`() = checkByCode(
        """
        module 0x1::main {
            struct S { x: u64 }
            struct T { x: u64 }
            fun receiver(self: T, y: u64): u64 {
                self.x + y
            }
            fun test_call_styles(s: S): u64 {
                s.receiver(1)
                  //^ unresolved
            }
        }        
    """
    )

    fun `test cannot be resolved if self requires mutable reference`() = checkByCode(
        """
        module 0x1::main {
            struct S { x: u64 }
            fun receiver(self: &mut S, y: u64): u64 {
                self.x + y
            }
            fun test_call_styles(s: &S): u64 {
                s.receiver(1)
                  //^ unresolved
            }
        }        
    """
    )

    fun `test cannot be resolved if self requires no reference`() = checkByCode(
        """
        module 0x1::main {
            struct S { x: u64 }
            fun receiver(self: S, y: u64): u64 {
                self.x + y
            }
            fun test_call_styles(s: &mut S): u64 {
                s.receiver(1)
                  //^ unresolved
            }
        }        
    """
    )

    fun `test receiver resolvable if self requires reference but mut reference exists`() = checkByCode(
        """
        module 0x1::main {
            struct S { x: u64 }
            fun receiver(self: &S, y: u64): u64 {
                //X
                self.x + y
            }
            fun test_call_styles(s: &mut S): u64 {
                s.receiver(1)
                  //^
            }
        }        
    """
    )

    fun `test public receiver style from another module`() = checkByCode(
        """
        module 0x1::m {
            struct S { x: u64 }
            public fun receiver(self: S, y: u64): u64 {
                         //X
                self.x + y
            }
        }
        module 0x1::main {
            use 0x1::m::S;
            
            fun test_call_styles(s: S): u64 {
                s.receiver(1)
                  //^
            }
        }
    """
    )

    fun `test unresolved private receiver style from another module`() = checkByCode(
        """
        module 0x1::m {
            struct S { x: u64 }
            fun receiver(self: S, y: u64): u64 {
                self.x + y
            }
        }
        module 0x1::main {
            use 0x1::m::S;
            
            fun test_call_styles(s: S): u64 {
                s.receiver(1)
                  //^ unresolved
            }
        }
    """
    )

    fun `test resolve receiver style with generic argument`() = checkByCode(
        """
        module 0x1::main {
            struct S<T> { field: T }
            fun receiver<T>(self: S<T>): T {
               //X
                self.field
            }
            fun main(s: S<u8>) {
                s.receiver()
                  //^
            }
        }        
    """
    )

    fun `test method cannot be resolved if self is not the first parameter`() = checkByCode(
        """
        module 0x1::main {
            struct S { x: u64 }
            fun receiver(y: u64, self: &S): u64 {
                self.x + y
            }
            fun test_call_styles(s: S): u64 {
                s.receiver(&s)
                  //^ unresolved
            }
        }                
    """
    )
}