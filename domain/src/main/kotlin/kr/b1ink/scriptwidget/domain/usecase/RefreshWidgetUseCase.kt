package kr.b1ink.scriptwidget.domain.usecase

import kr.b1ink.scriptwidget.domain.model.RefreshTrigger
import kr.b1ink.scriptwidget.domain.model.RenderState
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import kr.b1ink.scriptwidget.domain.model.WidgetError
import kr.b1ink.scriptwidget.domain.model.WidgetFamily
import kr.b1ink.scriptwidget.domain.repository.RenderStateRepository
import kr.b1ink.scriptwidget.domain.repository.ScriptRepository
import kr.b1ink.scriptwidget.domain.repository.WidgetBindingRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kr.b1ink.scriptwidget.domain.model.ScriptRequirements
import kr.b1ink.scriptwidget.domain.scheduler.NetworkState
import kr.b1ink.scriptwidget.domain.scheduler.RefreshPolicy
import kr.b1ink.scriptwidget.domain.scheduler.ScreenState
import kr.b1ink.scriptwidget.domain.scheduler.WidgetRefreshScheduler
import kr.b1ink.scriptwidget.domain.time.Clock
import java.util.concurrent.ConcurrentHashMap

/** Runs the script bound to a widget and publishes what the widget should display. */
class RefreshWidgetUseCase(
    private val bindings: WidgetBindingRepository,
    private val scripts: ScriptRepository,
    private val runScript: RunScriptUseCase,
    private val renderStates: RenderStateRepository,
    private val scheduler: WidgetRefreshScheduler,
    private val clock: Clock,
    private val screen: ScreenState = ScreenState.AlwaysOn,
    private val network: NetworkState = NetworkState.AlwaysConnected,
    private val settings: kr.b1ink.scriptwidget.domain.scheduler.RefreshSettings =
        kr.b1ink.scriptwidget.domain.scheduler.RefreshSettings.Default,
) {
    /** One run at a time per widget; a background run waiting behind another usually finds nothing to do. */
    private val locks = ConcurrentHashMap<Int, Mutex>()

    suspend operator fun invoke(
        appWidgetId: Int,
        family: WidgetFamily,
        trigger: RefreshTrigger = RefreshTrigger.Manual,
        action: String? = null,
    ): RenderState = locks.getOrPut(appWidgetId) { Mutex() }.withLock {
        if (trigger == RefreshTrigger.Periodic) {
            notYetDue(appWidgetId)?.let { return@withLock it }
        }
        if (trigger != RefreshTrigger.Manual) {
            // Events and refreshAfterDate both retry when the screen turns on.
            val retries = trigger == RefreshTrigger.Scheduled || trigger == RefreshTrigger.Event
            waitingForScreen(appWidgetId, isScheduled = retries)?.let { return@withLock it }
            // Something changed: an event doesn't wait out the minimum interval.
            if (trigger != RefreshTrigger.Event) {
                tooSoon(appWidgetId, isScheduled = trigger == RefreshTrigger.Scheduled)?.let { return@withLock it }
            }
            waitingForNetwork(appWidgetId)?.let { return@withLock it }
        }
        val state = computeState(appWidgetId, family, action)
        renderStates.publish(appWidgetId, state)
        // Every bound widget keeps one pending refresh: the script's refreshAfterDate, or a catch-up.
        if (state.scriptName != null) {
            val requested = state.tree?.refreshAfter?.takeIf { state.error == null }
                ?: (clock.now() + RefreshPolicy.DEFAULT_INTERVAL_MS)
            scheduleNext(appWidgetId, requested = requested, lastRun = clock.now())
        }
        state
    }

    private fun sleepsWhileScreenOff() = !screen.isInteractive() && !settings.refreshWhileScreenOff()

    /**
     * Screen off: nobody sees the widget, so background runs wait. The retry alarm doesn't wake the
     * device; it fires when the screen turns on, and the widget refreshes then.
     */
    private suspend fun waitingForScreen(appWidgetId: Int, isScheduled: Boolean): RenderState? {
        if (!sleepsWhileScreenOff()) return null
        val current = renderStates.get(appWidgetId)?.takeIf { it.updatedAt > 0 } ?: return null
        if (isScheduled) scheduler.refreshAt(appWidgetId, clock.now() + RefreshPolicy.SCREEN_OFF_RETRY_MS, wakeUp = false)
        return current
    }

    /**
     * Background runs keep a minimum interval (5 min with the screen on, 15 min when allowed with it off).
     * A skipped scheduled run is moved to the earliest allowed time.
     */
    private suspend fun tooSoon(appWidgetId: Int, isScheduled: Boolean): RenderState? {
        val current = renderStates.get(appWidgetId)?.takeIf { it.updatedAt > 0 } ?: return null
        val minInterval = RefreshPolicy.minInterval(screenOn = screen.isInteractive())
        if (clock.now() - current.updatedAt >= minInterval - RefreshPolicy.EARLY_TOLERANCE_MS) return null
        if (isScheduled) {
            scheduleNext(appWidgetId, requested = current.tree?.refreshAfter ?: 0, lastRun = current.updatedAt)
        }
        return current
    }

    /** Offline, a script that loads from the web would only fail: keep what the widget shows. */
    private suspend fun waitingForNetwork(appWidgetId: Int): RenderState? {
        if (network.isConnected()) return null
        val current = renderStates.get(appWidgetId) ?: return null
        val script = bindings.get(appWidgetId)?.let { scripts.read(it.scriptName) } ?: return null
        return current.takeIf { ScriptRequirements.usesNetwork(script) }
    }

    /**
     * Alarms never wake the device, except when the user lets widgets refresh while the screen is
     * off: then at most every 15 minutes.
     */
    private fun scheduleNext(appWidgetId: Int, requested: Long, lastRun: Long) {
        val wakeUp = !screen.isInteractive() && settings.refreshWhileScreenOff()
        val at = maxOf(requested, lastRun + RefreshPolicy.minInterval(screenOn = !wakeUp))
        scheduler.refreshAt(appWidgetId, at, wakeUp = wakeUp)
    }

    /** The current state, if the script asked not to be refreshed yet (`refreshAfterDate`). */
    private suspend fun notYetDue(appWidgetId: Int): RenderState? {
        val current = renderStates.get(appWidgetId) ?: return null
        val refreshAfter = current.tree?.refreshAfter ?: return null
        val binding = bindings.get(appWidgetId) ?: return null
        return current.takeIf { it.scriptName == binding.scriptName && clock.now() < refreshAfter }
    }

    private suspend fun computeState(appWidgetId: Int, family: WidgetFamily, action: String?): RenderState {
        val binding = bindings.get(appWidgetId)
            ?: return RenderState(error = NOT_BOUND_ERROR, updatedAt = clock.now())
        val script = scripts.read(binding.scriptName)
            ?: return RenderState(
                scriptName = binding.scriptName,
                error = WidgetError.encode(WidgetError.ScriptNotFound(binding.scriptName)),
                updatedAt = clock.now(),
            )

        val result = runScript(
            script = script,
            env = ScriptEnvironment(
                runsInWidget = true,
                widgetFamily = family,
                widgetParameter = binding.parameter,
                cacheKey = "widget-$appWidgetId",
                widgetAction = action,
            ),
            scriptName = binding.scriptName,
        )
        val lastGoodTree = renderStates.get(appWidgetId)
            ?.takeIf { it.scriptName == binding.scriptName }
            ?.tree
        return RenderState(
            scriptName = binding.scriptName,
            tree = result.widget ?: lastGoodTree.takeIf { !result.isSuccess },
            error = result.error,
            updatedAt = clock.now(),
            timings = result.timings,
            family = family,
        )
    }

    companion object {
        val NOT_BOUND_ERROR = WidgetError.encode(WidgetError.NotBound)
    }
}
