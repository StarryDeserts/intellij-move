package org.sui.ide.annotator.errors

import org.sui.ide.annotator.MvErrorAnnotator
import org.sui.ide.inspections.MvTypeCheckInspection
import org.sui.utils.tests.WithEnabledInspections
import org.sui.utils.tests.annotation.AnnotatorTestCase

class NeedsTypeAnnotationTest: AnnotatorTestCase(MvErrorAnnotator::class) {

    fun `test type parameter can be inferred from mut vector ref`() = checkErrors("""
        module 0x1::m {
            fun swap<T>(v: &mut vector<T>) {
                swap(v);
            }
        }        
    """)

    fun `test no type annotation error if name is unresolved but type is inferrable`() = checkErrors("""
        module 0x1::m {
            struct Option<Element> has copy, drop, store {
                vec: vector<Element>
            }
            native fun is_none<Element>(t: &Option<Element>): bool;
            fun main() {
                is_none(unknown_name);
            }
        }        
    """)

    fun `test no type annotation error if return type is unresolved but type is inferrable`() = checkErrors("""
        module 0x1::m {
            struct Option<Element> has copy, drop, store {
                vec: vector<Element>
            }
            native fun none<Element>(): Option<Element>;
            fun main() {
                none() == unknown_name;
            }
        }        
    """)

    fun `test no needs type annotation for spec struct field item passed`() = checkErrors("""
        module 0x1::m {
            struct Option<Element> has copy, drop, store {
                vec: vector<Element>
            }
            native fun is_none<Element>(t: &Option<Element>): bool;
            struct S { aggregator: Option<u8> }
            spec S {
                is_none(aggregator);
            }
        }        
    """)

    fun `test no error if schema params are inferrable`() = checkErrors("""
    module 0x1::M {
        struct Token<Type> {}
        spec schema MySchema<Type> {
            token: Token<Type>;
        }
        fun call() {}
        spec call {
            include MySchema { token: Token<u8> };
        }
    }        
    """)

    fun `test no need for explicit type infer from return type`() = checkErrors(
        """
        module 0x1::m {
            struct Coin<CoinType> { val: u8 }
            struct S<X> { coins: Coin<X> }
            struct BTC {}
            fun coin_zero<ZeroCoinType>(): Coin<ZeroCoinType> { Coin<ZeroCoinType> { val: 0 } }
            fun call<CallCoinType>() {
                S<CallCoinType> { coins: coin_zero() };
            }
        }        
    """
    )

    fun `test explicit generic required for uninferrable type params`() = checkErrors("""
    module 0x1::M {
        fun call<R>() {}
        fun m() {
            <error descr="Could not infer this type. Try adding an annotation">call</error>();
        }
    }    
    """)

    @WithEnabledInspections(MvTypeCheckInspection::class)
    fun `test no needs type annotation error if type error happened in the child`() = checkErrors("""
        module 0x1::m {
            fun call<R>(a: u8, b: &R) {}
            fun main() {
                call(1, <error descr="Incompatible type 'bool', expected '&R'">false</error>);
            }
        }    
    """)

    fun `test no need type annotation error if function has missing value parameters`() = checkErrors("""
        module 0x1::m {
            fun call<R>(a: u8, b: &R) {}
            fun main() {
                call(<error descr="This function takes 2 parameters but 0 parameters were supplied">)</error>;
            }
        }    
    """)

    fun `test needs type annotation if missing parameters but not inferrable`() = checkErrors("""
        module 0x1::m {
            fun call<R>(a: u8) {}
            fun main() {
                <error descr="Could not infer this type. Try adding an annotation">call</error>(<error descr="This function takes 1 parameter but 0 parameters were supplied">)</error>;
            }
        }    
    """)
}
