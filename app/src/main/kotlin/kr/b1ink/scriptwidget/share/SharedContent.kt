package kr.b1ink.scriptwidget.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import kr.b1ink.scriptwidget.domain.model.ShareInput

/** Content another app shared to a script: texts, links, images and files (`args.plainTexts`, `args.urls`, …). */
object SharedContent {
    data class File(val path: String, val mimeType: String?)

    private val URL = Regex("""https?://[^\s<>"']+""")
    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "heic", "heif", "bmp")

    fun classify(texts: List<String>, files: List<File>): ShareInput {
        val plainTexts = mutableListOf<String>()
        val urls = mutableListOf<String>()
        texts.map(String::trim).filter(String::isNotEmpty).forEach { text ->
            val links = URL.findAll(text).map { it.value }.toList()
            if (links.singleOrNull() == text) {
                urls += text
            } else {
                plainTexts += text
                urls += links
            }
        }
        val (images, others) = files.partition { file ->
            file.mimeType?.startsWith("image/")
                ?: (file.path.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS)
        }
        return ShareInput(plainTexts, urls, others.map { it.path }, images.map { it.path })
    }

    /** Copies shared streams under [tmpDirectory]/share/<time>/ so scripts can read them with FileManager. */
    fun read(context: Context, intent: Intent, tmpDirectory: String): ShareInput {
        val resolver = context.contentResolver
        val target = java.io.File(tmpDirectory, "share/${System.currentTimeMillis()}").apply { mkdirs() }
        val files = streams(intent).mapNotNull { uri ->
            runCatching {
                val name = displayName(context, uri)
                val file = unique(target, name)
                resolver.openInputStream(uri)?.use { input -> file.outputStream().use(input::copyTo) } ?: return@runCatching null
                File(file.path, resolver.getType(uri))
            }.getOrNull()
        }
        return classify(texts(intent), files)
    }

    private fun texts(intent: Intent): List<String> {
        val many = intent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT)
        if (many != null) return many.map(CharSequence::toString)
        intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.let { return listOf(it.toString()) }
        return intent.clipData.items().mapNotNull { item -> item.text?.toString()?.takeIf { item.uri == null } }
    }

    private fun streams(intent: Intent): List<Uri> {
        val extras: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND_MULTIPLE ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                else @Suppress("DEPRECATION") intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            else -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            )
        }
        return extras.ifEmpty { intent.clipData.items().mapNotNull { it.uri } }
    }

    private fun ClipData?.items(): List<ClipData.Item> = if (this == null) emptyList() else List(itemCount, ::getItemAt)

    private fun displayName(context: Context, uri: Uri): String {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
        return name?.replace('/', '_')?.replace('\\', '_')?.trimStart('.')?.takeIf { it.isNotBlank() } ?: "shared"
    }

    private fun unique(dir: java.io.File, name: String): java.io.File {
        var file = java.io.File(dir, name)
        var n = 2
        while (file.exists()) file = java.io.File(dir, "${name.substringBeforeLast('.')} $n.${name.substringAfterLast('.', "bin")}").also { n++ }
        return file
    }
}
