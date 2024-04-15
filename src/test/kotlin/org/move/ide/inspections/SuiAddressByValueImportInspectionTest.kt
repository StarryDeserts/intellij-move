package org.sui.ide.inspections

import org.sui.utils.tests.annotation.InspectionProjectTestBase

class SuiAddressByValueImportInspectionTest : InspectionProjectTestBase(AddressByValueImportInspection::class) {
    fun `test no inspection if imported from the correct address name`() =
        checkWeakWarningsByFileTree {
            moveToml(
                """
        [package]
        name = "MyPackage"
        [addresses]
        std = "0x1"
        aptos_std = "0x1"
        """
            )
            sources {
                main(
                    """
                module aptos_std::main {
                    use std::debug/*caret*/;
                    fun main() {
                        debug::print();
                    }
                }    
                """
                )
                move(
                    "debug.move", """
            module std::debug { 
                public native fun print();
            }    
            """
                )
            }
        }

    fun `test no inspection if std value is _ in the dependency`() =
        checkWeakWarningsByFileTree {
            dir("stdlib") {
                moveToml(
                    """
        [package]
        name = "Stdlib"
        [addresses]
        std = "_"
        [dev-addresses]
        std = "0x1"
        """
                )
                sources {
                    move(
                        "debug.move", """
            module std::debug { 
                public native fun print();
            }    
            """
                    )
                }
            }
            moveToml(
                """
        [package]
        name = "MyPackage"
        
        [addresses]
        std = "0x1"

        [dependencies]
        Stdlib = { local = "./stdlib" } 
        """
            )
            sources {
                main(
                    """
                module std::main {
                    use std::debug/*caret*/;
                    fun main() {
                        debug::print();
                    }
                }    
                """
                )
            }
        }

    fun `test no inspection if imported from the different address name with different value`() =
        checkWeakWarningsByFileTree {
            moveToml(
                """
        [package]
        name = "MyPackage"
        [addresses]
        std = "0x1"
        aptos_std = "0x1"
        std2 = "0x2"
        """
            )
            sources {
                main(
                    """
                module aptos_std::main {
                    use std2::debug/*caret*/;
                    fun main() {
                        debug::print();
                    }
                }    
                """
                )
                move(
                    "debug.move", """
            module std::debug { 
                public native fun print();
            }    
            """
                )
            }
        }

    fun `test fail if imported from the different address name but same value`() = checkFixByFileTree(
        "Change address to `std`",
        {
            moveToml(
                """
        [package]
        name = "MyPackage"
        [addresses]
        std = "0x1"
        aptos_std = "0x1"
        """
            )
            sources {
                main(
                    """
                module aptos_std::main {
                    use <weak_warning descr="Module is declared with a different address `std`">aptos_std::debug/*caret*/</weak_warning>;
                    fun main() {
                        debug::print();
                    }
                }    
                """
                )
                move(
                    "debug.move", """
            module std::debug { 
                public native fun print();
            }    
            """
                )
            }
        }, """
                module aptos_std::main {
                    use std::debug;
                    fun main() {
                        debug::print();
                    }
                }    
        """, checkWeakWarn = true
    )
}
