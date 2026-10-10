package com.arjun.mooary.ui.components

import androidx.compose.runtime.Composable
import com.arjun.mooary.core.error.AppError
import com.arjun.mooary.resources.Res
import com.arjun.mooary.resources.error_conflict
import com.arjun.mooary.resources.error_export
import com.arjun.mooary.resources.error_network
import com.arjun.mooary.resources.error_not_found
import com.arjun.mooary.resources.error_not_supported
import com.arjun.mooary.resources.error_permission_denied
import com.arjun.mooary.resources.error_storage
import com.arjun.mooary.resources.error_unexpected
import com.arjun.mooary.resources.error_validation
import org.jetbrains.compose.resources.stringResource

/** Turns domain failures into localised, action-oriented copy rather than Kotlin debug text. */
@Composable
fun AppError.userMessage(): String = stringResource(
    when (this) {
        AppError.Network -> Res.string.error_network
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
