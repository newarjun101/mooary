package com.arjun.soul_note.ui.components

import androidx.compose.runtime.Composable
import com.arjun.soul_note.core.error.AppError
import com.arjun.soul_note.core.error.AuthenticationReason
import com.arjun.soul_note.resources.Res
import com.arjun.soul_note.resources.error_authentication
import com.arjun.soul_note.resources.error_conflict
import com.arjun.soul_note.resources.error_export
import com.arjun.soul_note.resources.error_network
import com.arjun.soul_note.resources.error_not_found
import com.arjun.soul_note.resources.error_not_supported
import com.arjun.soul_note.resources.error_permission_denied
import com.arjun.soul_note.resources.error_storage
import com.arjun.soul_note.resources.error_unexpected
import com.arjun.soul_note.resources.error_validation
import org.jetbrains.compose.resources.stringResource

/** Turns domain failures into localised, action-oriented copy rather than Kotlin debug text. */
@Composable
fun AppError.userMessage(): String = stringResource(
    when (this) {
        AppError.Network -> Res.string.error_network
        is AppError.Authentication -> when (reason) {
            AuthenticationReason.INVALID_EMAIL,
            AuthenticationReason.WEAK_PASSWORD,
            -> Res.string.error_validation
            else -> Res.string.error_authentication
        }
        AppError.PermissionDenied -> Res.string.error_permission_denied
        AppError.NotFound -> Res.string.error_not_found
        is AppError.Validation -> Res.string.error_validation
        is AppError.Conflict -> Res.string.error_conflict
        is AppError.Storage -> Res.string.error_storage
        is AppError.Export -> Res.string.error_export
        is AppError.Unexpected -> if (reasonKey == "error_not_supported") {
            Res.string.error_not_supported
        } else {
            Res.string.error_unexpected
        }
    },
)
