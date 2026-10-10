package com.arjun.mooary.ui.profile

import com.arjun.mooary.ui.adaptive.adaptiveColumns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import com.arjun.mooary.resources.Res
import com.arjun.mooary.resources.common_off
import com.arjun.mooary.resources.common_on
import com.arjun.mooary.resources.nav_profile
import com.arjun.mooary.resources.nav_settings
import com.arjun.mooary.resources.profile_appearance_section
import com.arjun.mooary.resources.profile_conflicts
import com.arjun.mooary.resources.profile_daily_reminders
import com.arjun.mooary.domain.usecase.reminders.ReminderOutcome
import com.arjun.mooary.resources.profile_reminders_off
import com.arjun.mooary.resources.profile_reminders_permission_denied
import com.arjun.mooary.resources.profile_reminders_unsupported
import com.arjun.mooary.ui.permissions.rememberNotificationPermissionRequester
import com.arjun.mooary.resources.profile_data_section
import com.arjun.mooary.resources.profile_export_csv
import com.arjun.mooary.resources.common_dismiss
import com.arjun.mooary.resources.error_unexpected
import com.arjun.mooary.resources.profile_export_json
import com.arjun.mooary.resources.profile_export_notice
import com.arjun.mooary.resources.profile_export_saved
import com.arjun.mooary.resources.profile_last_synced
import com.arjun.mooary.resources.profile_never_synced
import com.arjun.mooary.resources.profile_not_signed_in
import com.arjun.mooary.resources.profile_on_device_title
import com.arjun.mooary.resources.profile_pending
import com.arjun.mooary.resources.profile_sync_now
import com.arjun.mooary.resources.profile_sync_section
import com.arjun.mooary.resources.profile_syncing
import com.arjun.mooary.resources.profile_syncing_ellipsis
import com.arjun.mooary.resources.profile_theme_dark
import com.arjun.mooary.resources.profile_theme_light
import com.arjun.mooary.resources.settings_language_title
import com.arjun.mooary.resources.developer_github
import com.arjun.mooary.resources.developer_github_handle
import com.arjun.mooary.resources.developer_github_url
import com.arjun.mooary.resources.developer_link_label
import com.arjun.mooary.resources.developer_linkedin
import com.arjun.mooary.resources.developer_linkedin_handle
import com.arjun.mooary.resources.developer_linkedin_url
import com.arjun.mooary.resources.developer_section
import com.arjun.mooary.resources.developer_phone_number
import com.arjun.mooary.resources.developer_phone
import com.arjun.mooary.resources.developer_phone_url
import com.arjun.mooary.resources.developer_email
import com.arjun.mooary.resources.developer_email_address
import com.arjun.mooary.resources.developer_email_url
import com.arjun.mooary.resources.developer_version
import com.arjun.mooary.resources.developer_version_value
import com.arjun.mooary.resources.version_info
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
import com.arjun.mooary.ui.components.AnimatedIcon
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
import com.arjun.mooary.domain.model.ExportFormat
import com.arjun.mooary.domain.repository.ThemeMode
import com.arjun.mooary.ui.base.SessionState
import com.arjun.mooary.ui.base.rememberSessionViewModel
import com.arjun.mooary.ui.components.MessageState
import com.arjun.mooary.ui.components.SectionHeader
import com.arjun.mooary.ui.components.Stat
import com.arjun.mooary.ui.components.StatTileGrid
import com.arjun.mooary.ui.components.userMessage
import com.arjun.mooary.ui.theme.JournalCard
import com.arjun.mooary.ui.theme.Spacing
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
    modifier: Modifier = Modifier,
) {
    val viewModel = rememberSessionViewModel<ProfileViewModel>(session.userId) {
        parametersOf(session.userId)
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
                text = stringResource(Res.string.profile_on_device_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(Res.string.profile_on_device_title),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                title = stringResource(Res.string.error_unexpected),
                message = error.userMessage(),
                icon = Icons.Default.Warning,
                actionLabel = stringResource(Res.string.common_dismiss),
                onAction = viewModel::dismissError,
            )
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
