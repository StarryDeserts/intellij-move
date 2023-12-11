package org.sui.lang.core.resolve

import org.sui.lang.core.psi.MvNamedElement
import org.sui.lang.core.psi.ext.isMsl
import org.sui.lang.core.psi.isVisibleInScope

data class SimpleScopeEntry<T : MvNamedElement>(
    val name: String,
    val element: T
)

fun interface MatchingProcessor<T : MvNamedElement> {
    fun match(entry: SimpleScopeEntry<T>): Boolean

    fun match(itemVis: ItemVis, element: T): Boolean {
        if (!itemVis.isMsl && element.isMsl()) return false
        if (!element.isVisibleInScope(itemVis.itemScope)) return false

        val name = element.name ?: return false
        val entry = SimpleScopeEntry(name, element)
        return match(entry)
    }

    fun matchAll(itemVis: ItemVis, vararg collections: Iterable<T>): Boolean =
        sequenceOf(*collections)
            .flatten()
            .any { match(itemVis, it) }
}
