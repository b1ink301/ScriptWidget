package kr.b1ink.scriptwidget.ui.list

import kr.b1ink.scriptwidget.data.ai.AiProvider
import kr.b1ink.scriptwidget.di.AppContainer
import kr.b1ink.scriptwidget.domain.ai.AiWidgetResult
import kr.b1ink.scriptwidget.domain.model.ScriptRunResult

/** [AiWidgetBackend] over the app's settings, engine and model clients. */
class ContainerAiBackend(private val container: AppContainer) : AiWidgetBackend {
    private val settings get() = container.aiSettings

    override var provider: AiProvider
        get() = settings.provider
        set(value) { settings.provider = value }

    override fun model(provider: AiProvider) = settings.model(provider)
    override fun setModel(provider: AiProvider, model: String) = settings.setModel(provider, model)
    override fun keyHint(provider: AiProvider) = settings.key(provider)?.takeLast(4)
    override fun setKey(provider: AiProvider, key: String) = settings.setKey(provider, key)

    override suspend fun models(provider: AiProvider): List<String> =
        container.aiModels(provider, checkNotNull(settings.key(provider)) { "No API key" })

    override suspend fun prompt(request: String) = container.aiPrompt(request)
    override suspend fun run(code: String): ScriptRunResult = container.aiPreview(code)
    override suspend fun generate(request: String, onAttempt: (Int) -> Unit): AiWidgetResult = container.generateWidget(request, onAttempt)
}
