package com.example.localplayer

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// =====================================================================
// Создание стемов: выбор, прогресс, итог
// =====================================================================

/** Окно из меню трека «⋮»: что выделить, и при нехватке моделей — их загрузка прямо здесь. */
@Composable
fun StemStartDialog(vm: PlayerViewModel, track: Track, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var selected by remember { mutableStateOf(Stems.loadSelection(ctx)) }
    LaunchedEffect(Unit) { Stems.refreshModels(ctx) }

    val busy = Stems.busy
    val locked = Stems.blocked
    val tree = vm.folder
    val missing = Stems.missingFor(selected)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stems_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.stems_choose),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    // Быстрый выбор: всё, кроме вокала
                    TextButton(onClick = { selected = Stem.INSTRUMENTAL }, enabled = !busy && !locked) {
                        Text(stringResource(R.string.stem_instrumental))
                    }
                }
                Stem.entries.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .clickable(enabled = !busy && !locked) { selected = if (s in selected) selected - s else selected + s },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = s in selected, onCheckedChange = null, enabled = !busy && !locked)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(s.label), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text(
                    stringResource(R.string.stems_choose_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                when {
                    locked -> Text(
                        stringResource(R.string.stems_locked_power),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    tree == null -> Text(
                        stringResource(R.string.stems_need_folder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    busy && Stems.phase == Stems.Phase.MODELS -> StemProgressBody()
                    busy -> Text(
                        stringResource(R.string.stems_busy),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    missing.isNotEmpty() -> Text(
                        stringResource(
                            R.string.stems_models_missing,
                            missing.joinToString(", ") { AppLocale.string(ctx, it.label) },
                            Formatter.formatShortFileSize(ctx, missing.sumOf { it.bytes }),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    else -> Text(
                        stringResource(R.string.stems_where, Stems.FOLDER),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !locked && tree != null && selected.isNotEmpty() && !busy,
                onClick = {
                    Stems.saveSelection(ctx, selected)
                    if (missing.isNotEmpty()) {
                        // Загрузка идёт прямо в этом окне; после неё кнопка станет «Начать»
                        Stems.showProgress = false
                        Stems.downloadModels(ctx, missing)
                    } else {
                        if (tree != null && Stems.start(ctx, track, selected, tree)) Stems.showProgress = true
                        onDismiss()
                    }
                },
            ) {
                Text(stringResource(if (missing.isNotEmpty()) R.string.stems_download_models else R.string.stems_start))
            }
        },
        dismissButton = {
            TextButton(onClick = { if (busy && Stems.phase == Stems.Phase.MODELS) Stems.cancel() else onDismiss() }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/** Окно с полосой прогресса. «Скрыть» закрывает окно, работа продолжается (остаётся полоса внизу и уведомление). */
@Composable
fun StemProgressDialog() {
    if (!Stems.busy || !Stems.showProgress) return
    AlertDialog(
        onDismissRequest = { Stems.showProgress = false },
        title = {
            Text(stringResource(if (Stems.phase == Stems.Phase.MODELS) R.string.stems_models_title else R.string.stems_title))
        },
        text = { StemProgressBody() },
        confirmButton = { TextButton(onClick = { Stems.showProgress = false }) { Text(stringResource(R.string.stems_hide)) } },
        dismissButton = { TextButton(onClick = { Stems.cancel() }) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Содержимое окна прогресса: название трека, полоса, процент, этап, оценка времени. */
@Composable
private fun StemProgressBody() {
    val ctx = LocalContext.current
    val p = Stems.progress.coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Stems.track?.let {
            Text(it.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
        Text("${(p * 100).toInt()}%  ·  ${Stems.statusText(ctx)}", style = MaterialTheme.typography.bodyMedium)
        val eta = Stems.etaSeconds
        if (Stems.phase == Stems.Phase.SEPARATING && eta >= 0) {
            Text(
                stringResource(R.string.stems_eta, formatEta(eta)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (Stems.phase != Stems.Phase.MODELS) {
            Text(
                stringResource(R.string.stems_background_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Тонкая полоса над нижней панелью, пока работа идёт, а окно прогресса скрыто. Нажатие открывает окно. */
@Composable
fun StemBanner() {
    if (!Stems.busy || Stems.showProgress) return
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth().clickable { Stems.showProgress = true },
    ) {
        Column {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GraphicEq, null, Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.stems_banner, (Stems.progress * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LinearProgressIndicator(progress = { Stems.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Итог: готово, отменено, ошибка или нет права записи в папку с музыкой (тогда предлагаем выбрать её заново). */
@Composable
fun StemNoticeDialog(pickMusicFolder: () -> Unit) {
    val ctx = LocalContext.current
    val n = Stems.notice ?: return
    val needWrite = n.kind == StemNotice.Kind.NEED_WRITE
    AlertDialog(
        onDismissRequest = { Stems.notice = null },
        title = { Text(n.title(ctx)) },
        text = { Text(n.message(ctx)) },
        confirmButton = {
            TextButton(
                onClick = {
                    Stems.notice = null
                    if (needWrite) pickMusicFolder()
                },
            ) { Text(stringResource(if (needWrite) R.string.choose_folder else R.string.close)) }
        },
        dismissButton = if (needWrite) {
            @Composable {
                TextButton(onClick = { Stems.notice = null }) { Text(stringResource(R.string.cancel)) }
            }
        } else {
            null
        },
    )
}

// =====================================================================
// Настройки: модели
// =====================================================================

/** Раздел настроек «Стемы (ИИ)»: состояние трёх моделей, загрузка, импорт из файлов, удаление. */
@Composable
fun StemSettingsSection() {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { Stems.refreshModels(ctx) }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) Stems.importModels(ctx, uris)
    }
    val busy = Stems.busy
    val locked = Stems.blocked
    val missing = StemModel.entries.filter { it !in Stems.installed }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.stems_section_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.stems_hint, Stems.FOLDER),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (locked) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.stems_locked_power),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.stems_models_title), style = MaterialTheme.typography.bodyLarge)
                StemModel.entries.forEach { m ->
                    val ok = m in Stems.installed
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(m.label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (ok) stringResource(R.string.stems_model_installed)
                            else Formatter.formatShortFileSize(ctx, m.bytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (busy && Stems.phase == Stems.Phase.MODELS) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StemProgressBody()
                    TextButton(onClick = { Stems.cancel() }) { Text(stringResource(R.string.cancel)) }
                }
            }
        }

        if (missing.isNotEmpty()) {
            Button(
                onClick = { Stems.showProgress = false; Stems.downloadModels(ctx, missing) },
                enabled = !busy && !locked,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.CloudDownload, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        R.string.stems_download_models_size,
                        Formatter.formatShortFileSize(ctx, missing.sumOf { it.bytes }),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        OutlinedButton(
            onClick = {
                AppLock.skipNextLock()
                importPicker.launch(arrayOf("*/*"))
            },
            enabled = !busy && !locked,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.FileOpen, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.stems_import_models), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (Stems.installed.isNotEmpty() && !busy) {
            TwoStepButton(stringResource(R.string.stems_delete_models), stringResource(R.string.stems_delete_confirm)) {
                Stems.deleteModels(ctx)
            }
        }
        Text(
            stringResource(R.string.stems_license),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** «4:32» из числа секунд. */
private fun formatEta(sec: Long): String {
    val s = sec.coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
