package com.example.localplayer

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Блок «Загрузка из облака» в настройках, внутри раздела «Папка с музыкой».
 * Показывается, только если при сборке задан ключ приложения Dropbox.
 */
@Composable
fun CloudSection(vm: PlayerViewModel, pickMusicFolder: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { CloudAuth.syncState(ctx) }

    val ids = CloudId.entries.filter { it.configured }
    if (ids.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text(stringResource(R.string.cloud_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.cloud_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for (id in ids) ProviderCard(vm, id)
        CloudProgress()
    }
    CloudDialogs(pickMusicFolder)
}

@Composable
private fun ProviderCard(vm: PlayerViewModel, id: CloudId) {
    val ctx = LocalContext.current
    val connected = id in CloudAuth.connected
    val pick = CloudAuth.folders[id]
    var chooser by remember { mutableStateOf(false) }
    val busy = CloudSync.busy

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Cloud, null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(id.title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(if (connected) R.string.cloud_connected else R.string.cloud_not_connected),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (connected) {
                    TextButton(onClick = { CloudAuth.disconnect(ctx, id) }, enabled = !busy) {
                        Text(stringResource(R.string.cloud_disconnect))
                    }
                } else {
                    Button(
                        onClick = {
                            if (!CloudAuth.begin(ctx, id)) {
                                CloudSync.notice = CloudNotice(CloudNotice.Kind.NO_BROWSER, id)
                            }
                        },
                        enabled = !busy,
                    ) { Text(stringResource(R.string.cloud_connect)) }
                }
            }

            if (connected) {
                val folderText = pick?.name?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.cloud_folder_root)
                OutlinedButton(
                    onClick = { chooser = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.FolderOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.cloud_choose_folder),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Текущая папка в облаке: подпись под кнопкой выбора
                Text(
                    stringResource(R.string.cloud_folder_label, folderText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Button(
                    onClick = { vm.folder?.let { CloudSync.prepare(ctx, it, id) } },
                    enabled = vm.folder != null && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.CloudDownload, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.cloud_download, id.title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (vm.folder == null) {
                    Text(
                        stringResource(R.string.cloud_need_music_folder),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (chooser) {
        CloudFolderDialog(
            id = id,
            onDismiss = { chooser = false },
            onPick = { folderId, name ->
                CloudAuth.setFolder(ctx, id, folderId, name)
                chooser = false
            },
        )
    }
}

@Composable
private fun CloudProgress() {
    val phase = CloudSync.phase
    if (phase != CloudSync.Phase.LISTING && phase != CloudSync.Phase.DOWNLOADING) return
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(CloudSync.provider?.title.orEmpty(), style = MaterialTheme.typography.bodyLarge)
            if (phase == CloudSync.Phase.LISTING) {
                Text(stringResource(R.string.cloud_checking), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                val total = CloudSync.filesTotal.coerceAtLeast(1)
                val done = CloudSync.filesDone
                Text(
                    stringResource(R.string.cloud_progress, done, CloudSync.filesTotal),
                    style = MaterialTheme.typography.bodySmall,
                )
                LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                Text(
                    CloudSync.current,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = { CloudSync.cancel() }) { Text(stringResource(R.string.cancel)) }
        }
    }
}

@Composable
private fun CloudDialogs(pickMusicFolder: () -> Unit) {
    val ctx = LocalContext.current

    val plan = CloudSync.plan
    if (plan != null && CloudSync.phase == CloudSync.Phase.CONFIRM) {
        AlertDialog(
            onDismissRequest = { CloudSync.discard() },
            title = { Text(stringResource(R.string.cloud_confirm_title, plan.provider.title)) },
            text = {
                Text(
                    stringResource(
                        R.string.cloud_confirm_body,
                        plan.items.size,
                        Formatter.formatShortFileSize(ctx, plan.bytes),
                        plan.skipped,
                        plan.provider.folderName,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { CloudSync.confirm(ctx) }) { Text(stringResource(R.string.cloud_start)) }
            },
            dismissButton = {
                TextButton(onClick = { CloudSync.discard() }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    val notice = CloudSync.notice
    if (notice != null) {
        val needWrite = notice.kind == CloudNotice.Kind.NEED_WRITE
        AlertDialog(
            onDismissRequest = { CloudSync.notice = null },
            title = { Text(notice.title(ctx)) },
            text = { Text(notice.message(ctx)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        CloudSync.notice = null
                        if (needWrite) pickMusicFolder()
                    },
                ) { Text(stringResource(if (needWrite) R.string.choose_folder else R.string.close)) }
            },
            dismissButton = if (needWrite) {
                @Composable {
                    TextButton(onClick = { CloudSync.notice = null }) { Text(stringResource(R.string.cancel)) }
                }
            } else {
                null
            },
        )
    }
}

/** Выбор папки в облаке: заходим вглубь по подпапкам и выбираем текущую. Корень (пустой id) означает всё доступное приложению. */
@Composable
private fun CloudFolderDialog(id: CloudId, onDismiss: () -> Unit, onPick: (folderId: String, name: String) -> Unit) {
    val ctx = LocalContext.current
    val stack = remember { mutableStateListOf<CloudFolder>() }
    var list by remember { mutableStateOf<List<CloudFolder>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val here = stack.lastOrNull()

    LaunchedEffect(here?.id) {
        loading = true
        error = null
        try {
            list = withContext(Dispatchers.IO) {
                CloudProviders.create(ctx.applicationContext, id).listFolders(here?.id ?: "")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            list = emptyList()
            error = e.message ?: e.toString()
        }
        loading = false
    }

    val rowColors = ListItemDefaults.colors(containerColor = Color.Transparent)
    val path = (listOf(id.title) + stack.map { it.name }).joinToString(" / ")
    val err = error

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cloud_choose_folder)) },
        text = {
            Column {
                Text(
                    path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    if (stack.isNotEmpty()) {
                        ListItem(
                            modifier = Modifier.clickable { stack.removeAt(stack.lastIndex) },
                            leadingContent = { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) },
                            headlineContent = { Text("..") },
                            colors = rowColors,
                        )
                    }
                    when {
                        loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        err != null -> Text(
                            stringResource(R.string.cloud_error, err),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(8.dp),
                        )
                        list.isEmpty() -> Text(
                            stringResource(R.string.cloud_no_subfolders),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                        else -> list.forEach { f ->
                            ListItem(
                                modifier = Modifier.clickable { stack.add(f) },
                                leadingContent = { Icon(Icons.Default.Folder, null) },
                                headlineContent = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = rowColors,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(here?.id ?: "", stack.joinToString(" / ") { it.name }) }) {
                Text(stringResource(R.string.cloud_use_folder))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
