package org.sui.utils.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Disposer
import com.intellij.ui.PopupMenuListenerAdapter
import com.intellij.ui.dsl.builder.Cell
import java.awt.Component
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import javax.swing.JComboBox
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.text.JTextComponent

// TODO: remove when 2022.1 is dropped

fun <T, C : JComboBox<T>> Cell<C>.whenItemSelectedFromUi(
    parentDisposable: Disposable? = null,
    listener: (T) -> Unit
): Cell<C> {
    return applyToComponent { whenItemSelectedFromUiImpl(parentDisposable, listener) }
}

fun <T> JComboBox<T>.whenItemSelectedFromUiImpl(parentDisposable: Disposable? = null, listener: (T) -> Unit) {
    whenPopupMenuWillBecomeInvisible(parentDisposable) {
        invokeLater(ModalityState.stateForComponent(this)) {
            selectedItem?.let {
                @Suppress("UNCHECKED_CAST")
                listener(it as T)
            }
        }
    }
}

fun <T : JTextComponent> Cell<T>.whenTextChangedFromUi(
    parentDisposable: Disposable? = null,
    listener: (String) -> Unit
): Cell<T> {
    return applyToComponent { whenTextChangedFromUi(parentDisposable, listener) }
}

fun JTextComponent.whenTextChangedFromUi(parentDisposable: Disposable? = null, listener: (String) -> Unit) {
    whenKeyReleased(parentDisposable) {
        invokeLater(ModalityState.stateForComponent(this)) {
            listener(text)
        }
    }
}

fun Component.whenKeyReleased(parentDisposable: Disposable? = null, listener: (KeyEvent) -> Unit) {
    addKeyListener(parentDisposable, object : KeyAdapter() {
        override fun keyReleased(e: KeyEvent) = listener(e)
    })
}

fun Component.addKeyListener(parentDisposable: Disposable? = null, listener: KeyListener) {
    addKeyListener(listener)
    parentDisposable?.whenDisposed {
        removeKeyListener(listener)
    }
}

fun JComboBox<*>.whenPopupMenuWillBecomeInvisible(
    parentDisposable: Disposable? = null,
    listener: (PopupMenuEvent) -> Unit
) {
    addPopupMenuListener(parentDisposable, object : PopupMenuListenerAdapter() {
        override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) = listener(e)
    })
}

fun JComboBox<*>.addPopupMenuListener(parentDisposable: Disposable? = null, listener: PopupMenuListener) {
    addPopupMenuListener(listener)
    parentDisposable?.whenDisposed {
        removePopupMenuListener(listener)
    }
}

private fun Disposable.whenDisposed(listener: () -> Unit) {
    Disposer.register(this) { listener() }
}

object NavigationUtilCompat {
    fun hidePopupIfDumbModeStarts(popup: JBPopup, project: Project) {
        if (!DumbService.isDumb(project)) {
            project.messageBus.connect(popup)
                .subscribe(DumbService.DUMB_MODE, object: DumbService.DumbModeListener {
                    override fun enteredDumbMode() {
                        popup.cancel()
                    }
                })
        }
    }
}
