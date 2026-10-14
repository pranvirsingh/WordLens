package com.pranvirsingh.wordlens.scan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pranvirsingh.wordlens.BuildConfig
import com.pranvirsingh.wordlens.R
import com.pranvirsingh.wordlens.settings.AppSettings
import com.pranvirsingh.wordlens.settings.ThemeMode
import com.pranvirsingh.wordlens.update.AvailableUpdate

/** The ⋮ menu on the scan screen: theme, online meanings on/off, and About. */
@Composable
fun SettingsMenu(
    settings: AppSettings,
    update: AvailableUpdate?,
    onShowUpdate: () -> Unit,
    onUpdateChecksChanged: (Boolean) -> Unit,
) {
    val themeMode by settings.themeMode.collectAsStateWithLifecycle()
    val onlineMeanings by settings.onlineMeanings.collectAsStateWithLifecycle()
    val updateChecks by settings.updateChecks.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { open = true },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.45f),
                contentColor = Color.White,
            ),
        ) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_menu))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (update != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.update_menu, update.version),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    onClick = {
                        open = false
                        onShowUpdate()
                    },
                )
                HorizontalDivider()
            }
            Text(
                text = stringResource(R.string.menu_theme),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
            )
            ThemeMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(stringResource(mode.labelRes())) },
                    leadingIcon = { RadioButton(selected = mode == themeMode, onClick = null) },
                    onClick = { settings.setThemeMode(mode) },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_online_meanings)) },
                trailingIcon = { Switch(checked = onlineMeanings, onCheckedChange = null) },
                onClick = { settings.setOnlineMeanings(!onlineMeanings) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_update_checks)) },
                trailingIcon = { Switch(checked = updateChecks, onCheckedChange = null) },
                onClick = { onUpdateChecksChanged(!updateChecks) },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_about)) },
                onClick = {
                    open = false
                    showAbout = true
                },
            )
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(stringResource(R.string.about_title, BuildConfig.VERSION_NAME)) },
            text = { Text(stringResource(R.string.about_body)) },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}
