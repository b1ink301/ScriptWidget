package kr.b1ink.scriptwidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.b1ink.scriptwidget.ui.config.WidgetConfigScreen
import kr.b1ink.scriptwidget.ui.config.WidgetConfigViewModel
import kr.b1ink.scriptwidget.ui.theme.ScriptWidgetTheme

/** Picks the script (and optional parameter) for a widget instance. Also used for reconfiguring. */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out must not leave an unconfigured widget behind.
        setResult(RESULT_CANCELED, resultIntent(appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val container = container
        setContent {
            ScriptWidgetTheme {
                val vm = viewModel {
                    WidgetConfigViewModel(
                        appWidgetId,
                        container.observeScriptNames,
                        container.seedSamples,
                        container.getWidgetBinding,
                        container.bindWidget,
                    )
                }
                val state by vm.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(state.done) {
                    if (state.done) {
                        setResult(RESULT_OK, resultIntent(appWidgetId))
                        finish()
                    }
                }
                WidgetConfigScreen(
                    state = state,
                    onSelect = vm::select,
                    onParameterChange = vm::onParameterChange,
                    onConfirm = vm::confirm,
                )
            }
        }
    }

    private fun resultIntent(appWidgetId: Int) = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}
