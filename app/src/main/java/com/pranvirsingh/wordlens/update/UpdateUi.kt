package com.pranvirsingh.wordlens.update

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.pranvirsingh.wordlens.R
import java.util.Locale

/** A small pill under the top bar: "WordLens 1.2.0 is out — see what's new". */
@Composable
fun UpdateBanner(
    update: AvailableUpdate,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 6.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .clickable(onClick = onOpen)
                    .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.update_banner, update.version),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_close),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** What's new in the update, with a button that downloads it. */
@Composable
fun UpdateDialog(update: AvailableUpdate, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.SystemUpdate, contentDescription = null) },
        title = { Text(stringResource(R.string.update_title, update.version)) },
        text = {
            Text(
                text = update.notes.ifBlank { stringResource(R.string.update_no_notes) },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    context.openDownload(update)
                },
            ) {
                Text(stringResource(R.string.update_download, megabytes(update.apkBytes)))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) }
        },
    )
}

/**
 * Hands the APK link to the browser, which downloads it and offers to install
 * it — the same signed app, so Android installs it as an update over this one.
 */
private fun Context.openDownload(update: AvailableUpdate) {
    // Checked again here: only ever open an https link on GitHub.
    if (!UpdateChecker.isTrustedLink(update.apkUrl)) return
    val download = Intent(Intent.ACTION_VIEW, update.apkUrl.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(download)
    } catch (_: ActivityNotFoundException) {
        // No browser: put the release page on the clipboard so it can be pasted anywhere.
        getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), update.pageUrl))
        Toast.makeText(this, getString(R.string.update_link_copied), Toast.LENGTH_LONG).show()
    }
}

private fun megabytes(bytes: Long): String =
    if (bytes <= 0) "" else String.format(Locale.getDefault(), " (%.0f MB)", bytes / 1_000_000.0)
