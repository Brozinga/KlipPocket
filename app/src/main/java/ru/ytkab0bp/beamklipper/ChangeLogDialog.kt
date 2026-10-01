package ru.ytkab0bp.beamklipper

import android.app.Dialog
import ru.ytkab0bp.beamklipper.ui.theme.PaperAlt
import ru.ytkab0bp.beamklipper.ui.theme.Line
import ru.ytkab0bp.beamklipper.ui.theme.KlipShape
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ru.ytkab0bp.beamklipper.ui.components.KlipButton
import ru.ytkab0bp.beamklipper.ui.theme.Ink
import ru.ytkab0bp.beamklipper.ui.theme.Paper
import ru.ytkab0bp.beamklipper.utils.ChangelogText
import java.nio.charset.StandardCharsets
import java.util.Locale

class ChangeLogDialog(context: Context) : Dialog(context) {
    private val changelogText: String? = runCatching {
        context.assets.open("update.json").use { inp ->
            ChangelogText.pick(inp.readBytes().toString(StandardCharsets.UTF_8), Locale.getDefault().language)
        }
    }.getOrNull()

    override fun show() {
        if (changelogText == null) {
            dismiss()
            return
        }
        setContentView(
            androidx.compose.ui.platform.ComposeView(context).apply {
                setContent {
                    ru.ytkab0bp.beamklipper.ui.theme.KlipTheme {
                        ChangelogHost(changelogText!!) { dismiss() }
                    }
                }
            }
        )
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        window?.setGravity(Gravity.BOTTOM)
        super.show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangelogHost(text: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalRippleConfiguration provides null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(PaperAlt, KlipShape)
                    .border(2.dp, Line, KlipShape)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.Changelog),
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Ink,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .height(300.dp)
                            .verticalScroll(rememberScrollState())
                    )
                    Spacer(Modifier.height(16.dp))
                    KlipButton(
                        text = stringResource(R.string.ChangelogOK),
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
