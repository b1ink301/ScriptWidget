package kr.b1ink.scriptwidget.ui.designer

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kr.b1ink.scriptwidget.R
import kr.b1ink.scriptwidget.di.AppContainer
import kr.b1ink.scriptwidget.domain.model.FontResult
import kr.b1ink.scriptwidget.domain.usecase.AddFileBookmarkUseCase
import kr.b1ink.scriptwidget.domain.usecase.AddFontUseCase
import kr.b1ink.scriptwidget.domain.usecase.ExportForGalleryUseCase
import kr.b1ink.scriptwidget.pin.WidgetPinner

@Composable
fun DesignerRoute(container: AppContainer, scriptName: String, onBack: () -> Unit, onShowCode: () -> Unit) {
    val context = LocalContext.current
    val designerPinUnsupported = androidx.compose.ui.res.stringResource(R.string.designer_pin_unsupported)
    val scope = rememberCoroutineScope()
    val vm = viewModel(key = "designer-$scriptName") {
        DesignerViewModel(
            scriptName,
            container.openDesign,
            container.saveDesign,
            container.designGenerator,
            container.runPreview,
            bookmarks = container.fileBookmarks,
            addBookmark = AddFileBookmarkUseCase(container.fileBookmarks, container.scriptDocuments),
            fonts = container.fonts,
            addFont = AddFontUseCase(container.fonts, container.scriptDocuments),
        )
    }
    val pickFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addFont(uri.toString(), displayName(context, uri) ?: "font.ttf")
    }
    var pictureTarget by remember { mutableStateOf(PictureTarget.SelectedImage) }
    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.addPicture(uri.toString(), pictureTarget)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val exported = androidx.compose.ui.res.stringResource(R.string.designer_exported_gallery, "%s")
    val exportFailed = androidx.compose.ui.res.stringResource(R.string.designer_export_failed, "%s")
    val exportForGallery = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            vm.saveIfDirty()
            val message = runCatching { container.exportForGallery(scriptName, uri.toString()) }
                .fold({ exported.replace("%s", ExportForGalleryUseCase.fileName(scriptName)) }, { exportFailed.replace("%s", it.message ?: it.toString()) })
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    val notAFont = androidx.compose.ui.res.stringResource(R.string.designer_font_not_a_font)
    val fontFailed = androidx.compose.ui.res.stringResource(R.string.designer_font_failed, "%s")
    LaunchedEffect(state.fontProblem) {
        when (val problem = state.fontProblem) {
            FontResult.NotAFont -> Toast.makeText(context, notAFont, Toast.LENGTH_LONG).show()
            is FontResult.Failed -> Toast.makeText(context, fontFailed.replace("%s", problem.message), Toast.LENGTH_LONG).show()
            else -> return@LaunchedEffect
        }
        vm.fontProblemShown()
    }
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
            onDrop = vm::moveTo,
            onWrap = vm::wrapSelected,
            onRemove = vm::removeSelected,
            onExportForGallery = { exportForGallery.launch(ExportForGalleryUseCase.fileName(scriptName)) },
            onPickFont = { pickFont.launch(arrayOf("font/*", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream", "*/*")) },
            onPickPicture = { target ->
                pictureTarget = target
                pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        ),
    )
}

/** The picked file's name ("Pixel Sans.ttf"), which becomes the font's name. */
private fun displayName(context: android.content.Context, uri: android.net.Uri): String? =
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
