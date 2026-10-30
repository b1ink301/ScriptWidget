package kr.b1ink.scriptwidget.domain.scheduler

import kr.b1ink.scriptwidget.domain.model.TriggerEvent

/** Hooks into the system for exactly these events (and releases the others), so unused ones cost nothing. */
fun interface EventMonitor {
    fun watch(events: Set<TriggerEvent>)
}
