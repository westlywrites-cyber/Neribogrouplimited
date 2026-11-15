package com.westly.neribogroup.feature.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow

/**
 * Remembers where the person was on every screen (scroll place, search text, selected tab)
 * while they go into a deeper screen and come back.
 *
 * Why this exists: the app opens a screen by swapping the one on show for the new one, so the
 * old screen is thrown away and its own memory goes with it. Coming back built it again from
 * zero, at the top. This memory lives in the ViewModel, outside any screen, so it survives.
 *
 * Everything is filed under a "scope" (one per open place: the home page, a section, an app,
 * a staff member, a case...). When a place is really left, [retainOnly] forgets its scope, so
 * opening it again later starts fresh at the top.
 */
class NavMemory {
    private val scrolls = HashMap<String, HashMap<String, Pair<Int, Int>>>()
    private val values = HashMap<String, HashMap<String, MutableState<Any?>>>()

    internal fun scroll(scope: String, key: String): Pair<Int, Int>? = scrolls[scope]?.get(key)

    internal fun putScroll(scope: String, key: String, place: Pair<Int, Int>) {
        scrolls.getOrPut(scope) { HashMap() }[key] = place
    }

    @Suppress("UNCHECKED_CAST")
    internal fun <T> state(scope: String, key: String, initial: T): MutableState<T> {
        val bucket = values.getOrPut(scope) { HashMap() }
        return bucket.getOrPut(key) { mutableStateOf<Any?>(initial) } as MutableState<T>
    }

    /** Forgets every scope that is no longer open. */
    fun retainOnly(live: Set<String>) {
        scrolls.keys.retainAll(live)
        values.keys.retainAll(live)
    }

    fun clear() {
        scrolls.clear()
        values.clear()
    }
}

val LocalNavMemory = compositionLocalOf { NavMemory() }

/** Which open place the screen being drawn belongs to. Set once, around all the screens. */
val LocalNavScope = compositionLocalOf { "home" }

/** A list position that survives going into another screen and coming back. */
@Composable
fun rememberKeptListState(key: String = "list"): LazyListState {
    val memory = LocalNavMemory.current
    val scope = LocalNavScope.current
    val state = remember(memory, scope, key) {
        val saved = memory.scroll(scope, key)
        LazyListState(saved?.first ?: 0, saved?.second ?: 0)
    }
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { memory.putScroll(scope, key, it) }
    }
    return state
}

/** A value (search text, selected tab...) that survives going into another screen and coming back. */
@Composable
fun <T> rememberKept(key: String, initial: T): MutableState<T> {
    val memory = LocalNavMemory.current
    val scope = LocalNavScope.current
    return remember(memory, scope, key) { memory.state(scope, key, initial) }
}
