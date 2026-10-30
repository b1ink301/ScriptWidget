package kr.b1ink.scriptwidget.ui.designer

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kr.b1ink.scriptwidget.R
import kr.b1ink.scriptwidget.di.AppContainer
import kr.b1ink.scriptwidget.pin.WidgetPinner

@Composable
fun DesignerRoute(container: AppContainer, scriptName: String, onBack: () -> Unit, onShowCode: () -> Unit) {
    val context = LocalContext.current
    val designerPinUnsupported = androidx.compose.ui.res.stringResource(R.string.designer_pin_unsupported)
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
                        Toast.makeText(context, designerPinUnsupported, Toast.LENGTH_SHORT).show()
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
