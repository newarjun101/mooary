package com.arjun.soul_note.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.arjun.soul_note.ui.components.AnimatedIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.arjun.soul_note.ui.base.rememberViewModel
import com.arjun.soul_note.ui.components.MessageState
import com.arjun.soul_note.ui.components.SectionHeader
import com.arjun.soul_note.ui.components.userMessage
import com.arjun.soul_note.ui.theme.JournalCard
import com.arjun.soul_note.ui.theme.DiaryShapes
import com.arjun.soul_note.ui.theme.Spacing
import com.arjun.soul_note.domain.repository.AppLanguage
import com.arjun.soul_note.resources.Res
import com.arjun.soul_note.resources.flag_english
import com.arjun.soul_note.resources.flag_myanmar
import com.arjun.soul_note.resources.auth_error_title
import com.arjun.soul_note.resources.common_dismiss
import com.arjun.soul_note.resources.profile_cancel
import com.arjun.soul_note.resources.profile_sign_in_prompt
import com.arjun.soul_note.resources.settings_account_section
import com.arjun.soul_note.resources.settings_change_password
import com.arjun.soul_note.resources.settings_confirm_password
import com.arjun.soul_note.resources.settings_current_password
import com.arjun.soul_note.resources.settings_danger_section
import com.arjun.soul_note.resources.settings_delete_account
import com.arjun.soul_note.resources.settings_delete_confirm_body
import com.arjun.soul_note.resources.settings_delete_confirm_title
import com.arjun.soul_note.resources.settings_display_name_hint
import com.arjun.soul_note.resources.settings_display_name_label
import com.arjun.soul_note.resources.settings_display_name_save
import com.arjun.soul_note.resources.settings_language_burmese
import com.arjun.soul_note.resources.settings_language_english
import com.arjun.soul_note.resources.settings_language_title
import com.arjun.soul_note.resources.settings_local_only_note
import com.arjun.soul_note.resources.settings_name_saved
import com.arjun.soul_note.resources.settings_new_password
import com.arjun.soul_note.resources.settings_password_changed
import com.arjun.soul_note.resources.settings_photo_content_description
import com.arjun.soul_note.resources.settings_photo_removed
import com.arjun.soul_note.resources.settings_photo_updated
import com.arjun.soul_note.resources.settings_profile_section
import com.arjun.soul_note.resources.settings_security_section
import com.arjun.soul_note.resources.settings_sign_out
import com.arjun.soul_note.resources.settings_sign_out_confirm_body
import com.arjun.soul_note.resources.settings_sign_out_confirm_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.koin.core.parameter.parametersOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Account settings: the profile, the password, and the two destructive actions.
 *
 * Signing out and deleting the account moved here from the profile screen so that screen
 * stays a read-only summary. Both are confirmed first, and neither navigates on the tap:
 * deletion only leaves once it has actually finished, because leaving cancels it.
 */
@Composable
fun SettingsScreen(
    userId: String,
    email: String?,
    onOpenSignIn: () -> Unit,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = rememberViewModel<SettingsViewModel>(userId, email) {
        parametersOf(userId, email)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmingSignOut by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    // Leaving only once the deletion has actually finished keeps this view model -- and
    // therefore the coroutine performing it -- alive until then.
    LaunchedEffect(state.deleted) {
        if (state.deleted) onSignedOut()
    }

    // The notice is a one-shot acknowledgement, so it is consumed rather than left to
    // re-appear on every recomposition or state change.
    state.notice?.let {
        LaunchedEffect(it) { viewModel.consumeNotice() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
        JournalCard(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.padding(Spacing.medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                SectionHeader(title = stringResource(Res.string.settings_profile_section))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                ) {
                    Avatar(state.displayName, state.photoPath ?: state.photoUrl)
                    Column {
                        Text(
                            text = state.displayName.ifBlank {
                                stringResource(Res.string.settings_display_name_label)
                            },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        state.email?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = state.displayName,
                    onValueChange = viewModel::onDisplayNameChanged,
                    label = { Text(stringResource(Res.string.settings_display_name_label)) },
                    supportingText = {
                        Text(
                            text = state.errorFor(SettingsFieldKeys.NAME)
                                ?: stringResource(Res.string.settings_display_name_hint),
                        )
                    },
                    isError = state.errorFor(SettingsFieldKeys.NAME) != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = viewModel::saveDisplayName,
                    enabled = state.canSaveName,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.settings_display_name_save))
                }
                state.notice?.let { NoticeRow(it) }
            }
        }

        if (!state.canManageAccount) {
            JournalCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(Spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(Spacing.small),
                ) {
                    SectionHeader(title = stringResource(Res.string.settings_account_section))
                    Text(
                        text = stringResource(Res.string.settings_local_only_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onOpenSignIn, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.profile_sign_in_prompt))
                    }
                }
            }
        }

        if (state.canManageAccount) {
            JournalCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(Spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(Spacing.small),
                ) {
                    SectionHeader(title = stringResource(Res.string.settings_security_section))
                    PasswordField(
                        value = state.currentPassword,
                        onValueChange = viewModel::onCurrentPasswordChanged,
                        label = stringResource(Res.string.settings_current_password),
                        error = state.errorFor(SettingsFieldKeys.CURRENT_PASSWORD),
                    )
                    PasswordField(
                        value = state.newPassword,
                        onValueChange = viewModel::onNewPasswordChanged,
                        label = stringResource(Res.string.settings_new_password),
                        error = state.errorFor(SettingsFieldKeys.NEW_PASSWORD),
                    )
                    PasswordField(
                        value = state.confirmPassword,
                        onValueChange = viewModel::onConfirmPasswordChanged,
                        label = stringResource(Res.string.settings_confirm_password),
                        error = state.errorFor(SettingsFieldKeys.CONFIRM_PASSWORD),
                    )
                    Button(
                        onClick = viewModel::submitPasswordChange,
                        enabled = state.canSubmitPassword,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(Res.string.settings_change_password))
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                SectionHeader(title = stringResource(Res.string.settings_danger_section))
                OutlinedButton(
                    onClick = { confirmingSignOut = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.settings_sign_out))
                }
                OutlinedButton(
                    onClick = { confirmingDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(Res.string.settings_delete_account),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        // Every failure on this screen is something the user cannot guess -- a rejected
        // password, a network that went away mid-change -- so it is shown rather than
        // swallowed. The raw error is what the rest of the app renders; see the note in
        // `MessageState`'s callers.
        state.error?.let {
            MessageState(
                title = stringResource(Res.string.auth_error_title),
                message = it.userMessage(),
                icon = Icons.Default.Warning,
                actionLabel = stringResource(Res.string.common_dismiss),
                onAction = viewModel::dismissError,
            )
        }
    }

    if (confirmingSignOut) {
        AlertDialog(
            onDismissRequest = { confirmingSignOut = false },
            title = { Text(stringResource(Res.string.settings_sign_out_confirm_title)) },
            text = { Text(stringResource(Res.string.settings_sign_out_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingSignOut = false
                    // Navigating here would clear this view model and cancel the sign-out,
                    // so the screen is left on the `deleted`-style success path instead.
                    viewModel.signOut()
                }) {
                    Text(stringResource(Res.string.settings_sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingSignOut = false }) {
                    Text(stringResource(Res.string.profile_cancel))
                }
            },
        )
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(Res.string.settings_delete_confirm_title)) },
            text = { Text(stringResource(Res.string.settings_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    viewModel.deleteAccount()
                }) {
                    Text(
                        text = stringResource(Res.string.settings_delete_account),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(Res.string.profile_cancel))
                }
            },
        )
    }
}

/**
 * Initials on a tinted disc, with the photo drawn over them once one loads.
 *
 * The initials are always painted underneath rather than used as a fallback slot, so a slow
 * or failed photo load shows the user's name instead of an empty disc. Coil is given the
 * path only: the bytes are fetched by the app's own Ktor client, which already owns the
 * refreshing Firebase id token, so Coil never needs a network fetcher or a second copy of
 * the auth logic.
 */
@Composable
private fun Avatar(displayName: String, photoUrl: String?, size: Dp = 48.dp) {
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = displayName.initials(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (photoUrl != null) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = stringResource(Res.string.settings_photo_content_description),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun NoticeRow(notice: SettingsNotice) {
    Text(
        text = stringResource(
            when (notice) {
                SettingsNotice.NAME_SAVED -> Res.string.settings_name_saved
                SettingsNotice.PASSWORD_CHANGED -> Res.string.settings_password_changed
                SettingsNotice.PHOTO_REMOVED -> Res.string.settings_photo_removed
                SettingsNotice.PHOTO_UPDATED -> Res.string.settings_photo_updated
            },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun LanguageSelector(
    selected: AppLanguage,
    onSelected: (AppLanguage) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = stringResource(
        if (selected == AppLanguage.ENGLISH) {
            Res.string.settings_language_english
        } else {
            Res.string.settings_language_burmese
        },
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            LanguageFlag(selected)
            Spacer(Modifier.width(Spacing.small))
            Text(selectedLabel, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            AnimatedIcon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AppLanguage.entries.forEach { language ->
                val label = stringResource(
                    if (language == AppLanguage.ENGLISH) {
                        Res.string.settings_language_english
                    } else {
                        Res.string.settings_language_burmese
                    },
                )
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = {
                        LanguageFlag(language)
                    },
                    trailingIcon = if (language == selected) {
                        { AnimatedIcon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        if (language != selected) onSelected(language)
                    },
                )
            }
        }
    }
}

@Composable
private fun LanguageFlag(language: AppLanguage) {
    val resource = if (language == AppLanguage.ENGLISH) {
        Res.drawable.flag_english
    } else {
        Res.drawable.flag_myanmar
    }
    val motion = rememberInfiniteTransition(label = "language-flag-${language.name}")
    val lift by motion.animateFloat(
        initialValue = -0.8f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "language-flag-lift",
    )
    Image(
        painter = painterResource(resource),
        contentDescription = null,
        modifier = Modifier.size(28.dp).graphicsLayer { translationY = lift * density },
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = {
            error?.let { Text(it) }
        },
        isError = error != null,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Up to two initials, so "Ada Lovelace" and "Ada" read the same. */
internal fun String.initials(): String = trim()
    .split(Regex("\\s+"))
    .filter { it.isNotEmpty() }
    .take(2)
    .map { it.first().uppercaseChar() }
    .joinToString("")
    .ifEmpty { "?" }
