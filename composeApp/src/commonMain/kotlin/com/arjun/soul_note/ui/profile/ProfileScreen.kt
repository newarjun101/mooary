package com.arjun.soul_note.ui.profile

import com.arjun.soul_note.ui.adaptive.adaptiveColumns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import com.arjun.soul_note.resources.Res
import com.arjun.soul_note.resources.common_off
import com.arjun.soul_note.resources.common_on
import com.arjun.soul_note.resources.nav_profile
import com.arjun.soul_note.resources.profile_account_section
import com.arjun.soul_note.resources.profile_appearance_section
import com.arjun.soul_note.resources.profile_cancel
import com.arjun.soul_note.resources.profile_conflicts
import com.arjun.soul_note.resources.profile_daily_reminders
import com.arjun.soul_note.domain.usecase.reminders.ReminderOutcome
import com.arjun.soul_note.resources.profile_reminders_off
import com.arjun.soul_note.resources.profile_reminders_permission_denied
import com.arjun.soul_note.resources.profile_reminders_unsupported
import com.arjun.soul_note.ui.permissions.rememberNotificationPermissionRequester
import com.arjun.soul_note.resources.profile_data_section
import com.arjun.soul_note.resources.profile_delete_account
import com.arjun.soul_note.resources.profile_delete_body
import com.arjun.soul_note.resources.profile_delete_confirm
import com.arjun.soul_note.resources.profile_delete_title
import com.arjun.soul_note.resources.profile_export_csv
import com.arjun.soul_note.resources.auth_error_title
import com.arjun.soul_note.resources.common_dismiss
import com.arjun.soul_note.resources.profile_export_json
import com.arjun.soul_note.resources.profile_export_notice
import com.arjun.soul_note.resources.profile_export_saved
import com.arjun.soul_note.resources.profile_last_synced
import com.arjun.soul_note.resources.profile_never_synced
import com.arjun.soul_note.resources.profile_not_signed_in
import com.arjun.soul_note.resources.profile_on_device_title
import com.arjun.soul_note.resources.profile_pending
import com.arjun.soul_note.resources.profile_sign_in_prompt
import com.arjun.soul_note.resources.profile_sign_out
import com.arjun.soul_note.resources.profile_sign_out_body
import com.arjun.soul_note.resources.profile_sign_out_title
import com.arjun.soul_note.resources.profile_sync_now
import com.arjun.soul_note.resources.profile_sync_section
import com.arjun.soul_note.resources.profile_syncing
import com.arjun.soul_note.resources.profile_syncing_ellipsis
import com.arjun.soul_note.resources.profile_theme_dark
import com.arjun.soul_note.resources.profile_theme_light
import com.arjun.soul_note.resources.settings_language_title
import com.arjun.soul_note.resources.developer_github
import com.arjun.soul_note.resources.developer_github_handle
import com.arjun.soul_note.resources.developer_github_url
import com.arjun.soul_note.resources.developer_link_label
import com.arjun.soul_note.resources.developer_linkedin
import com.arjun.soul_note.resources.developer_linkedin_handle
import com.arjun.soul_note.resources.developer_linkedin_url
import com.arjun.soul_note.resources.developer_section
import com.arjun.soul_note.resources.developer_phone_number
import com.arjun.soul_note.resources.developer_phone
import com.arjun.soul_note.resources.developer_phone_url
import com.arjun.soul_note.resources.developer_email
import com.arjun.soul_note.resources.developer_email_address
import com.arjun.soul_note.resources.developer_email_url
import com.arjun.soul_note.resources.developer_version
import com.arjun.soul_note.resources.developer_version_value
import com.arjun.soul_note.resources.version_info
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import com.arjun.soul_note.ui.components.AnimatedIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import com.arjun.soul_note.domain.model.ExportFormat
import com.arjun.soul_note.domain.repository.ThemeMode
import com.arjun.soul_note.ui.base.SessionState
import com.arjun.soul_note.ui.base.rememberViewModel
import com.arjun.soul_note.ui.components.MessageState
import com.arjun.soul_note.ui.components.SectionHeader
import com.arjun.soul_note.ui.components.Stat
import com.arjun.soul_note.ui.components.StatTileGrid
import com.arjun.soul_note.ui.components.userMessage
import com.arjun.soul_note.ui.settings.LanguageSelector
import com.arjun.soul_note.ui.theme.JournalCard
import com.arjun.soul_note.ui.theme.Spacing
import org.koin.core.parameter.parametersOf

/**
 * Account, sync, appearance, reminders and data export.
 *
 * Everything that can lose or move the user's data is on this screen behind a
 * confirmation, because "delete account" and "sign out" are the two buttons in a diary app
 * that must never be one stray tap away from the data.
 */
@Composable
fun ProfileScreen(
    session: SessionState,
    onSignIn: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = rememberViewModel<ProfileViewModel>(session.userId, session.user?.id) {
        parametersOf(session.userId, session.user)
    }
    val state by viewModel.state.collectAsState()
    val requester = rememberNotificationPermissionRequester()
    val uriHandler = LocalUriHandler.current
    val githubUrl = stringResource(Res.string.developer_github_url)
    val linkedInUrl = stringResource(Res.string.developer_linkedin_url)
    val phoneUrl = stringResource(Res.string.developer_phone_url)
    val emailUrl = stringResource(Res.string.developer_email_url)

    // Re-arm from the stored preference on every visit: platform alarms do not survive a
    // reboot, an app update or a force-stop, so the preference can be on with nothing
    // scheduled.
    LaunchedEffect(Unit) { viewModel.restoreReminderSchedule() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xSmall)) {
            Text(
                text = state.profile?.greetingName()
                    ?: session.user?.email
                    ?: stringResource(Res.string.profile_on_device_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(
                    if (session.isSignedIn) {
                        Res.string.profile_syncing
                    } else {
                        Res.string.profile_not_signed_in
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        JournalCard(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = com.arjun.soul_note.ui.theme.DiaryShapes.extraLarge,
        ) {
            Column(
                modifier = Modifier.padding(Spacing.medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                SectionHeader(
                    title = stringResource(Res.string.profile_sync_section),
                    trailing = {
                        TextButton(onClick = viewModel::syncNow, enabled = !state.sync.isSyncing) {
                            Text(
                                stringResource(
                                    if (state.sync.isSyncing) {
                                        Res.string.profile_syncing_ellipsis
                                    } else {
                                        Res.string.profile_sync_now
                                    },
                                ),
                            )
                        }
                    },
                )
                StatTileGrid(
                    stats = listOf(
                        Stat(
                            label = stringResource(Res.string.profile_pending),
                            value = state.sync.pendingCount.toString(),
                        ),
                        Stat(
                            label = stringResource(Res.string.profile_conflicts),
                            value = state.sync.conflictCount.toString(),
                        ),
                        Stat(
                            label = stringResource(Res.string.profile_last_synced),
                            value = state.sync.lastSyncedAt?.toString()?.take(10)
                                ?: stringResource(Res.string.profile_never_synced),
                            compact = true,
                        ),
                    ),
                    columns = adaptiveColumns(compact = 3, medium = 3, expanded = 3),
                )
            }
        }

        // Language belongs with the app-wide preferences, which is this top-level Settings
        // page. The selector updates Compose's locale in place, so the screen does not flash.
        JournalCard(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = com.arjun.soul_note.ui.theme.DiaryShapes.extraLarge,
        ) {
            Column(
                modifier = Modifier.padding(Spacing.medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                SectionHeader(title = stringResource(Res.string.settings_language_title))
                LanguageSelector(
                    selected = state.language,
                    onSelected = viewModel::setLanguage,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            SectionHeader(title = stringResource(Res.string.profile_appearance_section))
            ThemeSwitch(
                darkMode = state.themeMode == ThemeMode.DARK,
                onDarkModeChanged = { enabled ->
                    viewModel.setThemeMode(if (enabled) ThemeMode.DARK else ThemeMode.LIGHT)
                },
            )
        }

        ReminderSwitch(
            enabled = state.remindersEnabled,
            onEnabledChanged = { enabled ->
                if (!enabled) {
                    viewModel.setRemindersEnabled(false)
                } else {
                    // Ask before arming: the scheduler can only check permission, and a
                    // reminder armed without it would be delivered to nothing.
                    requester.request { granted ->
                        if (granted) {
                            viewModel.setRemindersEnabled(true)
                        } else {
                            viewModel.reportReminderRefused(ReminderOutcome.PERMISSION_DENIED)
                        }
                    }
                }
            },
        )

        state.reminderNotice?.let { outcome ->
            Text(
                text = stringResource(
                    when (outcome) {
                        ReminderOutcome.PERMISSION_DENIED -> Res.string.profile_reminders_permission_denied
                        ReminderOutcome.UNSUPPORTED -> Res.string.profile_reminders_unsupported
                        ReminderOutcome.ARMED,
                        ReminderOutcome.DISABLED,
                        -> Res.string.profile_reminders_off
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            SectionHeader(title = stringResource(Res.string.profile_data_section))
            Text(
                text = stringResource(Res.string.profile_export_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                Button(
                    onClick = { viewModel.export(ExportFormat.JSON) },
                    enabled = !state.isExporting,
                ) {
                    Text(stringResource(Res.string.profile_export_json))
                }
                OutlinedButton(
                    onClick = { viewModel.export(ExportFormat.CSV) },
                    enabled = !state.isExporting,
                ) {
                    Text(stringResource(Res.string.profile_export_csv))
                }
            }
            state.exportMessage?.let { message ->
                Text(
                    text = stringResource(Res.string.profile_export_saved, message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        JournalCard(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.padding(Spacing.medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                SectionHeader(title = stringResource(Res.string.developer_section))
                DeveloperLink(
                    label = stringResource(Res.string.developer_github),
                    value = stringResource(Res.string.developer_github_handle),
                    leading = { SocialLottieIcon("github.json") },
                    onClick = { uriHandler.openUri(githubUrl) },
                )
                DeveloperLink(
                    label = stringResource(Res.string.developer_linkedin),
                    value = stringResource(Res.string.developer_linkedin_handle),
                    leading = { SocialLottieIcon("linkedin.json") },
                    onClick = { uriHandler.openUri(linkedInUrl) },
                )
                DeveloperLink(
                    label = stringResource(Res.string.developer_phone),
                    value = stringResource(Res.string.developer_phone_number),
                    leading = { SocialLottieIcon("phone.json") },
                    onClick = { uriHandler.openUri(phoneUrl) },
                )
                DeveloperLink(
                    label = stringResource(Res.string.developer_email),
                    value = stringResource(Res.string.developer_email_address),
                    leading = { SocialLottieIcon("email.json") },
                    onClick = { uriHandler.openUri(emailUrl) },
                )
            }
        }

        JournalCard(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            DeveloperInfo(
                label = stringResource(Res.string.developer_version),
                value = stringResource(Res.string.developer_version_value),
            )
        }

        // Deleting an account and exporting are the two things on this screen that can
        // fail for reasons the user cannot guess. Both used to fail silently, because the
        // state carried an `error` that nothing rendered.
        state.error?.let { error ->
            MessageState(
                title = stringResource(Res.string.auth_error_title),
                message = error.userMessage(),
                icon = Icons.Default.Warning,
                actionLabel = stringResource(Res.string.common_dismiss),
                onAction = viewModel::dismissError,
            )
        }

        // Sign-out and account deletion live on the settings screen. They are destructive
        // and they are confirmation-gated, which does not belong on a read-only summary,
        // so this screen only offers the way through to them.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            SectionHeader(title = stringResource(Res.string.profile_account_section))
            // Offered whether or not there is an account: the settings screen is also where
            // the on-device diary sets a display name and reads why it is not syncing, so
            // hiding the way in when signed out would strand it.
            if (!session.isSignedIn) {
                Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.profile_sign_in_prompt))
                }
            }
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.nav_profile))
            }
        }
    }

}

@Composable
private fun DeveloperLink(
    label: String,
    value: String,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        leading()
        Text(
            text = stringResource(Res.string.developer_link_label, label, value),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.small),
        )
        AnimatedIcon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun SocialLottieIcon(fileName: String) {
    val composition by rememberLottieComposition(fileName) {
        LottieCompositionSpec.JsonString(Res.readBytes("files/$fileName").decodeToString())
    }
    Image(
        painter = rememberLottiePainter(
            composition = composition,
            iterations = Compottie.IterateForever,
        ),
        contentDescription = null,
        modifier = Modifier.size(26.dp),
    )
}

@Composable
private fun DeveloperInfo(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.small, vertical = Spacing.xSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VersionInfoIcon()
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.small),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VersionInfoIcon() {
    val transition = rememberInfiniteTransition(label = "versionInfo")
    val lift by transition.animateFloat(
        initialValue = 0f,
        targetValue = -3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "versionInfoLift",
    )
    val scale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "versionInfoScale",
    )
    Image(
        painter = painterResource(Res.drawable.version_info),
        contentDescription = null,
        modifier = Modifier
            .size(20.dp)
            .graphicsLayer {
                translationY = lift
                scaleX = scale
                scaleY = scale
            },
    )
}

@Composable
private fun ThemeSwitch(
    darkMode: Boolean,
    onDarkModeChanged: (Boolean) -> Unit,
) {
    JournalCard(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.medium, vertical = Spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedIcon(
                imageVector = if (darkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.small),
            ) {
                Text(
                    text = stringResource(
                        if (darkMode) Res.string.profile_theme_dark else Res.string.profile_theme_light,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(Res.string.profile_appearance_section),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = darkMode,
                onCheckedChange = onDarkModeChanged,
                thumbContent = {
                    AnimatedIcon(
                        imageVector = if (darkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun ReminderSwitch(
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
) {
    JournalCard(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.medium, vertical = Spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedIcon(
                imageVector = if (enabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.small),
            ) {
                Text(
                    text = stringResource(Res.string.profile_daily_reminders),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(if (enabled) Res.string.common_on else Res.string.common_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChanged,
                thumbContent = {
                    AnimatedIcon(
                        imageVector = if (enabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
    }
}
