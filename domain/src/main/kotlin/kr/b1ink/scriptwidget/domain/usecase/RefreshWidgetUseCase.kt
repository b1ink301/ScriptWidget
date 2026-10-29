package kr.b1ink.scriptwidget.domain.usecase

import kr.b1ink.scriptwidget.domain.model.RefreshTrigger
import kr.b1ink.scriptwidget.domain.model.RenderState
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
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
            tooSoon(appWidgetId, isScheduled = trigger == RefreshTrigger.Scheduled)?.let { return@withLock it }
            waitingForNetwork(appWidgetId)?.let { return@withLock it }
        }
        val state = computeState(appWidgetId, family, action)
        renderStates.publish(appWidgetId, state)
        state.tree?.refreshAfter
            ?.takeIf { state.error == null && it > clock.now() }
            ?.let { scheduleNext(appWidgetId, requested = it, lastRun = clock.now()) }
        state
    }

    /**
     * Background runs keep a minimum interval (5 min with the screen on, 15 min off) to save battery.
     * A skipped scheduled run is moved to the earliest allowed time.
     */
    private suspend fun tooSoon(appWidgetId: Int, isScheduled: Boolean): RenderState? {
        val current = renderStates.get(appWidgetId)?.takeIf { it.updatedAt > 0 } ?: return null
        val minInterval = RefreshPolicy.minInterval(screen.isInteractive())
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

    /** With the screen off the device may be woken (rarely); with it on, alarms wait for it to be awake. */
    private fun scheduleNext(appWidgetId: Int, requested: Long, lastRun: Long) {
        val screenOn = screen.isInteractive()
        val at = maxOf(requested, lastRun + RefreshPolicy.minInterval(screenOn))
        scheduler.refreshAt(appWidgetId, at, wakeUp = !screenOn)
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
                error = "스크립트 '${binding.scriptName}'를 찾을 수 없습니다.",
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
        const val NOT_BOUND_ERROR = "스크립트가 지정되지 않았습니다. 위젯을 길게 눌러 설정하세요."
    }
}
