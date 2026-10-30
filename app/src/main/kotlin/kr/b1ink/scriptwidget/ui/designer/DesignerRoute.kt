package kr.b1ink.scriptwidget.ui.designer

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kr.b1ink.scriptwidget.di.AppContainer
import kr.b1ink.scriptwidget.pin.WidgetPinner

@Composable
fun DesignerRoute(container: AppContainer, scriptName: String, onBack: () -> Unit, onShowCode: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm = viewModel(key = "designer-$scriptName") {
        DesignerViewModel(scriptName, container.openDesign, container.saveDesign, container.designGenerator, container.runPreview)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    DesignerScreen(
        state = state,
        actions = DesignerActions(
            onBack = { scope.launch { vm.saveIfDirty(); onBack() } },
            onUndo = vm::undo,
            onRedo = vm::redo,
            onShowCode = { scope.launch { vm.saveIfDirty(); onShowCode() } },
            onPin = {
                scope.launch {
                    vm.saveIfDirty()
                    if (!WidgetPinner.pin(context, scriptName, null)) {
                        Toast.makeText(context, "이 런처는 위젯 고정을 지원하지 않습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onSelect = vm::select,
            onFamily = vm::selectFamily,
            onAdd = vm::add,
            onUpdateSelected = { coalesce, change -> vm.updateSelected(coalesce, change) },
            onUpdateDesign = vm::updateDesign,
            onMove = vm::moveSelected,
            onWrap = vm::wrapSelected,
            onRemove = vm::removeSelected,
        ),
    )
}
