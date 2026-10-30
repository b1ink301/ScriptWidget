package kr.b1ink.scriptwidget.share

import kr.b1ink.scriptwidget.R
import androidx.compose.ui.res.stringResource
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.b1ink.scriptwidget.container
import kr.b1ink.scriptwidget.ui.theme.ScriptWidgetTheme

/**
 * "Run with script" in the system share sheet: pick a script, then it runs in the app with the
 * shared content as `args.plainTexts`, `args.urls`, `args.fileURLs` and `args.images`.
 */
class ShareToScriptActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = container
        setContent {
            ScriptWidgetTheme {
                val names by container.observeScriptNames().collectAsStateWithLifecycle()
                AlertDialog(
                    onDismissRequest = ::finish,
                    title = { Text(stringResource(R.string.share_pick_title)) },
                    text = {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                            items(names, key = { it }) { name ->
                                ListItem(modifier = Modifier.clickable { run(name) }, headlineContent = { Text(name) })
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = { TextButton(onClick = ::finish) { Text(stringResource(R.string.share_cancel)) } },
                )
            }
        }
    }

    private fun run(scriptName: String) {
        val shared = intent
        lifecycleScope.launch {
            // The read grant for shared URIs ends with this activity, so copy them now.
            val input = withContext(Dispatchers.IO) { SharedContent.read(this@ShareToScriptActivity, shared, container.scriptTemporaryDirectory) }
            startActivity(SharedRun.intent(this@ShareToScriptActivity, scriptName, input))
            finish()
        }
    }
}
