package kr.b1ink.scriptwidget.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kr.b1ink.scriptwidget.R
import kr.b1ink.scriptwidget.data.ai.AiProvider
import kr.b1ink.scriptwidget.domain.ai.AiWidget
import kr.b1ink.scriptwidget.domain.ai.AiWidgetResult
import kr.b1ink.scriptwidget.domain.ai.GenerateWidgetUseCase
import kr.b1ink.scriptwidget.domain.model.CatalogValidator
import kr.b1ink.scriptwidget.domain.model.ScriptRunResult
import kr.b1ink.scriptwidget.domain.model.WidgetFamily
import kr.b1ink.scriptwidget.ui.preview.WidgetPreview
import kr.b1ink.scriptwidget.ui.settings.REPOSITORY_URL

/** What the AI screen needs from the app: settings, the prompt, running scripts, the model call. */
interface AiWidgetBackend {
    var provider: AiProvider
    fun model(provider: AiProvider): String
    fun setModel(provider: AiProvider, model: String)

    /** The saved key's last characters, or null without a key. */
    fun keyHint(provider: AiProvider): String?
    fun setKey(provider: AiProvider, key: String)
    suspend fun models(provider: AiProvider): List<String>
    suspend fun prompt(request: String): String
    suspend fun run(code: String): ScriptRunResult
    suspend fun generate(request: String, onAttempt: (Int) -> Unit): AiWidgetResult
}

private val EXAMPLES = listOf(R.string.ai_example_weather, R.string.ai_example_dday, R.string.ai_example_battery, R.string.ai_example_rates)

/** A message to show, kept as a resource so it follows configuration changes. */
private class Note(val res: Int, vararg val args: Any)

/** A script the AI wrote and the app ran. */
private data class Tried(val code: String, val run: ScriptRunResult) {
    val works get() = run.error == null && run.widget != null
}

/**
 * "With AI": describe a widget, then either let the app call a model with the user's API key
 * (writing, running and fixing the script up to three times), or copy a prompt to any AI chat
 * app and paste the answer back. Either way the script runs on the real engine before saving.
 */
@Composable
fun AiWidgetDialog(backend: AiWidgetBackend, onSave: (name: String, code: String) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var request by remember { mutableStateOf("") }
    var useKey by remember { mutableStateOf(true) }
    var tried by remember { mutableStateOf<Tried?>(null) }
    var message by remember { mutableStateOf<Note?>(null) }
    var busy by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var answer by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    // Bumped when the provider, key or model changes, so "Create" sees the new settings.
    var settingsChanged by remember { mutableIntStateOf(0) }

    fun show(code: String, run: ScriptRunResult) {
        tried = Tried(code, run)
        if (name.isBlank()) name = AiWidget.name(request)
    }

    fun copy(text: String) {
        context.getSystemService(android.content.ClipboardManager::class.java)
            .setPrimaryClip(android.content.ClipData.newPlainText("ScriptWidget AI", text))
        android.widget.Toast.makeText(context, R.string.ai_copied, android.widget.Toast.LENGTH_SHORT).show()
    }

    fun launch(block: suspend () -> Unit) {
        busy = true
        message = null
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = Note(R.string.ai_generate_failed, e.message ?: e::class.java.simpleName)
            } finally {
                busy = false
            }
        }
    }

    fun generate() = launch {
        val result = backend.generate(request) { attempt = it }
        val code = result.code
        if (code == null) {
            message = Note(R.string.ai_no_code)
            return@launch
        }
        show(code, result.run ?: backend.run(code))
        result.error?.let { message = Note(R.string.ai_failed_after, result.attempts, it.lineSequence().first()) }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ai_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    val uri = LocalUriHandler.current
                    val guide = REPOSITORY_URL + stringResource(R.string.ai_guide_path)
                    TextButton(onClick = { runCatching { uri.openUri(guide) } }) { Text(stringResource(R.string.ai_guide)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.ai_close)) }
                }

                OutlinedTextField(
                    value = request,
                    onValueChange = { request = it },
                    label = { Text(stringResource(R.string.ai_request)) },
                    placeholder = { Text(stringResource(R.string.ai_request_hint)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("ai-request"),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EXAMPLES.forEachIndexed { index, example ->
                        val text = stringResource(example)
                        AssistChip(onClick = { request = text }, label = { Text(text, maxLines = 1) }, modifier = Modifier.testTag("ai-example-$index"))
                    }
                }

                PrimaryTabRow(selectedTabIndex = if (useKey) 0 else 1) {
                    Tab(selected = useKey, onClick = { useKey = true }, text = { Text(stringResource(R.string.ai_mode_key)) }, modifier = Modifier.testTag("ai-mode-key"))
                    Tab(selected = !useKey, onClick = { useKey = false }, text = { Text(stringResource(R.string.ai_mode_copy)) }, modifier = Modifier.testTag("ai-mode-copy"))
                }

                if (useKey) {
                    KeySettings(backend, onChange = { settingsChanged++ })
                    Button(
                        onClick = ::generate,
                        enabled = settingsChanged >= 0 && request.isNotBlank() && !busy &&
                            backend.keyHint(backend.provider) != null && backend.model(backend.provider).isNotBlank(),
                        modifier = Modifier.fillMaxWidth().testTag("ai-generate"),
                    ) { Text(stringResource(R.string.ai_generate)) }
                    if (busy) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.padding(4.dp))
                            Text(stringResource(R.string.ai_generating, attempt.coerceAtLeast(1), GenerateWidgetUseCase.MAX_ATTEMPTS))
                        }
                    }
                } else {
                    Text(stringResource(R.string.ai_copy_steps), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(
                        onClick = { launch { copy(backend.prompt(request)) } },
                        enabled = request.isNotBlank() && !busy,
                        modifier = Modifier.fillMaxWidth().testTag("ai-copy-prompt"),
                    ) { Text(stringResource(R.string.ai_copy_prompt)) }
                    OutlinedTextField(
                        value = answer,
                        onValueChange = { answer = it },
                        label = { Text(stringResource(R.string.ai_answer)) },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).testTag("ai-answer"),
                    )
                    Button(
                        onClick = {
                            val code = AiWidget.code(answer)
                            if (code == null) message = Note(R.string.ai_no_code)
                            else launch { show(code, backend.run(code)) }
                        },
                        enabled = answer.isNotBlank() && !busy,
                        modifier = Modifier.fillMaxWidth().testTag("ai-try"),
                    ) { Text(stringResource(R.string.ai_try)) }
                }

                message?.let { Text(stringResource(it.res, *it.args), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("ai-message")) }

                tried?.let { result ->
                    Result(result, name, onName = { name = it }, onSave = { onSave(name.trim(), result.code) })
                    if (!result.works) {
                        val fix = AiWidget.fixPrompt(result.code, result.run.error ?: GenerateWidgetUseCase.NO_WIDGET)
                        if (useKey) {
                            OutlinedButton(onClick = ::generate, enabled = !busy, modifier = Modifier.testTag("ai-retry")) {
                                Text(stringResource(R.string.ai_retry))
                            }
                        } else {
                            OutlinedButton(onClick = { copy(fix) }, modifier = Modifier.testTag("ai-copy-fix")) { Text(stringResource(R.string.ai_copy_fix)) }
                        }
                    }
                }
            }
        }
    }
}

/** Provider, API key and model for "In the app". */
@Composable
private fun KeySettings(backend: AiWidgetBackend, onChange: () -> Unit) {
    val scope = rememberCoroutineScope()
    var provider by remember { mutableStateOf(backend.provider) }
    var hint by remember(provider) { mutableStateOf(backend.keyHint(provider)) }
    var editingKey by remember(provider) { mutableStateOf(hint == null) }
    var key by remember(provider) { mutableStateOf("") }
    var model by remember(provider) { mutableStateOf(backend.model(provider)) }
    var models by remember(provider) { mutableStateOf<List<String>>(emptyList()) }
    var showModels by remember { mutableStateOf(false) }
    var modelError by remember { mutableStateOf<String?>(null) }
    val modelsFailed = stringResource(R.string.ai_models_failed, "%s")

    Text(stringResource(R.string.ai_provider), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AiProvider.entries.forEach { option ->
            FilterChip(
                selected = provider == option,
                onClick = { provider = option; backend.provider = option; onChange() },
                label = { Text(option.label) },
                modifier = Modifier.testTag("ai-provider-${option.name}"),
            )
        }
    }
    if (editingKey) {
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text(stringResource(R.string.ai_key)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            supportingText = { Text(stringResource(R.string.ai_key_note)) },
            trailingIcon = {
                TextButton(
                    onClick = { backend.setKey(provider, key); hint = backend.keyHint(provider); editingKey = hint == null; onChange() },
                    enabled = key.isNotBlank(),
                    modifier = Modifier.testTag("ai-key-ok"),
                ) {
                    Text("OK")
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("ai-key"),
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ai_key_saved, hint.orEmpty()), modifier = Modifier.weight(1f))
            TextButton(onClick = { editingKey = true; key = "" }) { Text(stringResource(R.string.ai_key_change)) }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = model,
            onValueChange = { model = it; backend.setModel(provider, it); onChange() },
            label = { Text(stringResource(R.string.ai_model)) },
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("ai-model"),
        )
        Box {
            OutlinedButton(
                onClick = {
                    modelError = null
                    scope.launch {
                        runCatching { backend.models(provider) }
                            .onSuccess { models = it; showModels = it.isNotEmpty() }
                            .onFailure { modelError = modelsFailed.replace("%s", it.message.orEmpty()) }
                    }
                },
                enabled = hint != null,
                modifier = Modifier.testTag("ai-models"),
            ) { Text(stringResource(R.string.ai_models_load)) }
            DropdownMenu(expanded = showModels, onDismissRequest = { showModels = false }) {
                models.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { model = option; backend.setModel(provider, option); showModels = false; onChange() })
                }
            }
        }
    }
    modelError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

private val AiProvider.label: String
    get() = when (this) {
        AiProvider.Gemini -> "Gemini"
        AiProvider.Claude -> "Claude"
        AiProvider.OpenAI -> "OpenAI"
    }

@Composable
private fun Result(result: Tried, name: String, onName: (String) -> Unit, onSave: () -> Unit) {
    val widget = result.run.widget
    if (widget != null) WidgetPreview(root = widget, family = WidgetFamily.Medium)
    if (result.works) {
        Text(stringResource(R.string.ai_works), color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("ai-works"))
    } else {
        Text(
            stringResource(R.string.ai_error, (result.run.error ?: GenerateWidgetUseCase.NO_WIDGET).lineSequence().first()),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("ai-error"),
        )
    }
    // What the script will reach for (network, location, calendar, ...) before it's saved.
    val uses = CatalogValidator.usedPermissions(result.code)
    if (uses.isNotEmpty()) Text(stringResource(R.string.ai_uses, uses.sorted().joinToString(", ")), style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(value = name, onValueChange = onName, label = { Text(stringResource(R.string.ai_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Button(onClick = onSave, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("ai-save")) { Text(stringResource(R.string.ai_save)) }
}
