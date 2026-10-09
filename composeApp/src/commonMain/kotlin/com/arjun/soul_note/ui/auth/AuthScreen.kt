package com.arjun.soul_note.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.arjun.soul_note.resources.Res
import com.arjun.soul_note.resources.auth_create_account
import com.arjun.soul_note.resources.auth_create_account_title
import com.arjun.soul_note.resources.auth_email
import com.arjun.soul_note.resources.auth_error_title
import com.arjun.soul_note.resources.auth_forgot_password
import com.arjun.soul_note.resources.auth_have_account
import com.arjun.soul_note.resources.auth_password
import com.arjun.soul_note.resources.auth_confirm_password
import com.arjun.soul_note.resources.auth_reset_sent
import com.arjun.soul_note.resources.auth_sign_in
import com.arjun.soul_note.resources.auth_subtitle
import com.arjun.soul_note.resources.auth_welcome_back
import com.arjun.soul_note.resources.common_dismiss
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material3.Button
import com.arjun.soul_note.ui.components.AnimatedIcon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.arjun.soul_note.ui.base.rememberViewModel
import com.arjun.soul_note.ui.components.MessageState
import com.arjun.soul_note.ui.components.userMessage
import com.arjun.soul_note.ui.theme.Spacing
import com.arjun.soul_note.ui.theme.DiaryShapes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Warning
import org.koin.core.parameter.parametersOf

/**
 * Sign-in, registration and password reset.
 *
 * The screen is reachable in every build, but a build with no Firebase configuration
 * cannot actually authenticate — the sign-in button reports the refusal from the offline
 * remote binding rather than failing silently, and the local diary stays available through
 * the "continue without an account" path.
 */
/**
 * One field treatment for the whole form, so the three inputs cannot drift apart.
 *
 * The container is the paper tone and the border a hairline in the scheme's own outline,
 * rather than Material's default heavy outline. Three dark-outlined boxes stacked on a
 * textured page is the part of this screen that used to read as a settings dialog.
 */
@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
)

@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = rememberViewModel<AuthViewModel> { parametersOf() }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.signedInUser) {
        if (state.signedInUser != null) onAuthenticated()
    }

    // The whole form sits in one centred card with a page-width cap. A bare form stretched
    // to a desktop window is the version of this screen that looks unfinished; a card that
    // stops growing reads as a deliberate page.
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.gutterWide)
            .widthIn(max = 460.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.large))
        Text(
            text = if (state.isRegister) {
                stringResource(Res.string.auth_create_account_title)
            } else {
                stringResource(Res.string.auth_welcome_back)
            },
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.auth_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Spacing.small))

        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChanged,
            label = { Text(stringResource(Res.string.auth_email)) },
            singleLine = true,
            isError = state.fieldErrors.containsKey("field_email"),
            supportingText = if (state.fieldErrors.containsKey("field_email")) {
                { Text(state.fieldErrors.getValue("field_email")) }
            } else {
                null
            },
            leadingIcon = { AnimatedIcon(Icons.Default.MailOutline, contentDescription = null) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            shape = DiaryShapes.large,
            colors = authFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChanged,
            label = { Text(stringResource(Res.string.auth_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            isError = state.fieldErrors.containsKey("field_password"),
            supportingText = if (state.fieldErrors.containsKey("field_password")) {
                { Text(state.fieldErrors.getValue("field_password")) }
            } else {
                null
            },
            leadingIcon = { AnimatedIcon(Icons.Default.Lock, contentDescription = null) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (state.isRegister) ImeAction.Next else ImeAction.Done,
            ),
            shape = DiaryShapes.large,
            colors = authFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.isRegister) {
            OutlinedTextField(
                value = state.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChanged,
                label = { Text(stringResource(Res.string.auth_confirm_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                isError = state.fieldErrors.containsKey("field_confirm_password"),
                supportingText = if (state.fieldErrors.containsKey("field_confirm_password")) {
                    { Text(state.fieldErrors.getValue("field_confirm_password")) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.isResetSent) {
            Text(
                text = stringResource(Res.string.auth_reset_sent),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        state.error?.let { error ->
            MessageState(
                title = stringResource(Res.string.auth_error_title),
                message = error.userMessage(),
                icon = Icons.Default.Warning,
                actionLabel = stringResource(Res.string.common_dismiss),
                onAction = viewModel::dismissError,
                modifier = Modifier.height(140.dp),
            )
        }

        Button(
            onClick = viewModel::submit,
            enabled = state.canSubmit,
            // Tall and pill-soft: this is the screen's one committed action, and it should
            // not be the same shape as the text field sitting directly above it.
            shape = DiaryShapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(
                    stringResource(
                        if (state.isRegister) Res.string.auth_create_account else Res.string.auth_sign_in,
                    ),
                )
            }
        }

        TextButton(onClick = {
            viewModel.switchMode(
                if (state.isRegister) AuthMode.SIGN_IN else AuthMode.REGISTER,
            )
        }) {
            Text(
                stringResource(
                    if (state.isRegister) Res.string.auth_have_account else Res.string.auth_create_account,
                ),
            )
        }

        if (!state.isRegister) {
            TextButton(onClick = viewModel::sendReset, enabled = state.email.isNotBlank()) {
                Text(stringResource(Res.string.auth_forgot_password))
            }
        }
    }
}
