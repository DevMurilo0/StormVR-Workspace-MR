package com.stormvr.app.vrroom

import android.view.View
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo

/** Resolves the focused editor without moving focus into the rendered keyboard. */
object VrTextInput {
    fun commit(root: View, text: String): Boolean {
        val target=root.findFocus() ?: root
        val connection=target.onCreateInputConnection(EditorInfo())
        if(connection!=null) return connection.commitText(text,1)
        val events=KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(text.toCharArray()) ?: return false
        return events.fold(true) { ok,e -> target.dispatchKeyEvent(e) && ok }
    }
    fun key(root: View, event: KeyEvent): Boolean = (root.findFocus() ?: root).dispatchKeyEvent(event)
}
