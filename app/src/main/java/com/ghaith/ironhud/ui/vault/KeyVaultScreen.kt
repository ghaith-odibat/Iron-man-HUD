package com.ghaith.ironhud.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ghaith.ironhud.HudViewModel
import com.ghaith.ironhud.ai.KeyHealth
import com.ghaith.ironhud.ai.KeyRow
import com.ghaith.ironhud.ai.ModelResolver
import com.ghaith.ironhud.ai.ProviderId
import com.ghaith.ironhud.ui.hud.HudButton
import com.ghaith.ironhud.ui.theme.Hud
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ProviderInfo(val title: String, val url: String, val hint: String)

private val INFO = mapOf(
    ProviderId.GEMINI to ProviderInfo(
        "GOOGLE GEMINI", "https://aistudio.google.com/apikey",
        "Primary uplink. Free Flash-Lite tier. Keys in the SAME Google Cloud project share one quota — create each key in a new project.",
    ),
    ProviderId.GROQ to ProviderInfo(
        "GROQ", "https://console.groq.com/keys",
        "Very fast Llama vision fallback. Keys in the same Groq organization share limits.",
    ),
    ProviderId.OPENROUTER to ProviderInfo(
        "OPENROUTER", "https://openrouter.ai/settings/keys",
        "Last-resort fallback via free vision models (openrouter/free router). Low daily cap.",
    ),
)

@Composable
fun KeyVaultScreen(vm: HudViewModel, onClose: () -> Unit) {
    val pool by vm.poolView.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val testResults = remember { mutableStateMapOf<String, String>() }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Hud.Black)
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText("◀ HUD", style = Hud.text(14.sp, weight = FontWeight.Bold),
                    modifier = Modifier.clickable(onClick = onClose).padding(end = 18.dp, top = 6.dp, bottom = 6.dp))
                BasicText("SECURE KEY VAULT", style = Hud.text(22.sp, weight = FontWeight.Bold, spacing = 3.sp))
            }
            Spacer(Modifier.height(6.dp))
            Note(
                "Keys are encrypted with the Android Keystore and never leave this tablet except to call their own " +
                    "provider. Add as many free keys as you like: when one hits its limit the HUD switches to the next " +
                    "automatically. ${pool.readyCount}/${pool.rows.size} keys ready."
            )

            Section("SYSTEMS")
            ToggleRow("AUTO-LOCK", "Identify whatever stays under the reticle for 0.8 s", settings.autoLock) { vm.setAutoLock(it) }
            ToggleRow("VOICE", "J.A.R.V.I.S. reads each brief aloud", settings.voice) { vm.setVoice(it) }
            ToggleRow("HOLOGRAM TINT", "Render the camera feed in HUD blue", settings.tint) { vm.setTint(it) }

            for ((position, provider) in settings.providerOrder.withIndex()) {
                val info = INFO.getValue(provider)
                Section("${position + 1}. ${info.title}", trailing = if (position > 0) "▲ PRIORITY" else null) {
                    vm.moveProviderUp(provider)
                }
                Note(info.hint)
                LinkLine("GET A FREE KEY: ${info.url}", info.url)

                val rows = pool.rows.filter { it.entry.provider == provider }
                if (rows.isEmpty()) Note("No keys yet.", alpha = 0.45f)
                for (row in rows) {
                    KeyRowView(
                        row = row,
                        now = now,
                        testResult = testResults[row.entry.id],
                        onTest = {
                            testResults[row.entry.id] = "TESTING…"
                            vm.testKey(row.entry) { r -> testResults[row.entry.id] = (if (r.ok) "✓ " else "✕ ") + r.message }
                        },
                        onReset = { vm.resetKey(row.entry.id) },
                        onDelete = { vm.removeKey(row.entry.id) },
                    )
                }
                AddKeyRow(provider) { secret, done -> vm.addKey(provider, secret, done) }
                ModelRow(
                    provider = provider,
                    current = settings.models[provider].orEmpty(),
                    active = vm.activeModel(provider),
                    onSave = { vm.setModel(provider, it) },
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Section(title: String, trailing: String? = null, onTrailing: () -> Unit = {}) {
    Spacer(Modifier.height(22.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(title, style = Hud.text(15.sp, weight = FontWeight.Bold, spacing = 2.sp), modifier = Modifier.weight(1f))
        if (trailing != null) {
            BasicText(trailing, style = Hud.text(11.sp, alpha = 0.8f),
                modifier = Modifier.clickable(onClick = onTrailing).padding(6.dp))
        }
    }
    Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp).height(1.dp).background(Hud.blue(0.5f)))
}

@Composable
private fun Note(text: String, alpha: Float = 0.7f) {
    BasicText(text, style = Hud.text(12.sp, alpha = alpha, glow = false).copy(lineHeight = 17.sp),
        modifier = Modifier.padding(vertical = 3.dp))
}

@Composable
private fun LinkLine(text: String, url: String) {
    val uri = LocalUriHandler.current
    BasicText(text, style = Hud.text(12.sp, weight = FontWeight.Bold, glow = false),
        modifier = Modifier.clickable { runCatching { uri.openUri(url) } }.padding(vertical = 6.dp))
}

@Composable
private fun ToggleRow(title: String, subtitle: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = Hud.text(13.sp, weight = FontWeight.Bold))
            BasicText(subtitle, style = Hud.text(11.sp, alpha = 0.6f, glow = false))
        }
        BasicText(
            if (on) "[ ON  ]" else "[ OFF ]",
            style = Hud.text(13.sp, alpha = if (on) 1f else 0.5f, weight = FontWeight.Bold),
        )
    }
}

@Composable
private fun KeyRowView(
    row: KeyRow,
    now: Long,
    testResult: String?,
    onTest: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
) {
    val s = row.status
    val badge = when (s.health) {
        KeyHealth.READY -> "READY"
        KeyHealth.COOLING -> "COOLING ${HudViewModel.formatSeconds(((s.until - now) / 1000).coerceAtLeast(0))}"
        KeyHealth.EXHAUSTED -> "DAILY LIMIT · BACK ${TIME.format(Date(s.until))}"
        KeyHealth.INVALID -> "REJECTED"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, Hud.blue(if (s.health == KeyHealth.READY) 0.6f else 0.3f), CutCornerShape(topEnd = 10.dp))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText("KEY ${row.indexInProvider}  ${row.entry.masked}", style = Hud.text(13.sp), modifier = Modifier.weight(1f))
            BasicText(badge, style = Hud.text(11.sp, weight = FontWeight.Bold, alpha = if (s.health == KeyHealth.READY) 1f else 0.6f))
        }
        s.note?.let { BasicText(it.take(160), style = Hud.text(10.sp, alpha = 0.5f, glow = false), modifier = Modifier.padding(top = 4.dp)) }
        testResult?.let { BasicText(it, style = Hud.text(11.sp, alpha = 0.9f, glow = false), modifier = Modifier.padding(top = 4.dp)) }
        BasicText(
            "USED ${s.successes} · FAILED ${s.failures}",
            style = Hud.text(9.sp, alpha = 0.45f, glow = false),
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HudButton("TEST", active = true, onClick = onTest)
            HudButton("RESET", active = s.health != KeyHealth.READY, onClick = onReset)
            HudButton("DELETE", active = false, onClick = onDelete)
        }
    }
}

@Composable
private fun AddKeyRow(provider: ProviderId, onAdd: (String, (String) -> Unit) -> Unit) {
    var text by remember(provider) { mutableStateOf("") }
    var message by remember(provider) { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        HudField(
            value = text,
            onValueChange = { text = it; message = null },
            placeholder = "PASTE ${provider.display} API KEY",
            secret = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.padding(start = 8.dp))
        HudButton("ADD", active = text.isNotBlank(), onClick = {
            onAdd(text) { result ->
                message = result
                if (result == "KEY STORED") text = ""
            }
        })
    }
    message?.let { BasicText(it, style = Hud.text(11.sp, alpha = 0.85f), modifier = Modifier.padding(top = 4.dp)) }
}

@Composable
private fun ModelRow(provider: ProviderId, current: String, active: String?, onSave: (String) -> Unit) {
    var text by remember(provider, current) { mutableStateOf(current) }
    val default = if (provider.defaultModel == ModelResolver.AUTO) "auto (newest free Flash-Lite)" else provider.defaultModel
    Column(Modifier.padding(top = 10.dp)) {
        BasicText(
            "MODEL · default: $default" + (active?.let { " · in use: $it" } ?: ""),
            style = Hud.text(10.sp, alpha = 0.6f, glow = false),
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            HudField(text, { text = it }, placeholder = "leave blank for default", modifier = Modifier.weight(1f))
            Spacer(Modifier.padding(start = 8.dp))
            HudButton("SAVE", active = text != current, onClick = { onSave(text) })
        }
    }
}

@Composable
private fun HudField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = Hud.text(13.sp, glow = false),
        cursorBrush = SolidColor(Hud.Blue),
        visualTransformation = if (secret) PasswordVisualTransformation('•') else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (secret) KeyboardType.Password else KeyboardType.Ascii,
            imeAction = ImeAction.Done,
            autoCorrectEnabled = false,
        ),
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                Modifier
                    .border(1.dp, Hud.blue(0.6f))
                    .background(Hud.blue(0.05f))
                    .padding(horizontal = 10.dp, vertical = 12.dp),
            ) {
                if (value.isEmpty()) BasicText(placeholder, style = Hud.text(12.sp, alpha = 0.35f, glow = false))
                inner()
            }
        },
    )
}

private val TIME = SimpleDateFormat("EEE HH:mm", Locale.US)
