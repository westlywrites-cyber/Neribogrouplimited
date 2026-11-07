package com.westly.neribogroup.feature.appdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.westly.neribogroup.data.AppUser
import com.westly.neribogroup.data.Plan
import com.westly.neribogroup.feature.common.ConfirmDialog

/** True when there is text before an "@", text after it, a "." after it, and no spaces. */
private fun isValidEmail(email: String): Boolean {
    if (email.isEmpty() || email.any { it.isWhitespace() }) return false
    val at = email.indexOf('@')
    if (at <= 0) return false
    val after = email.substring(at + 1)
    return after.isNotEmpty() && after.contains('.')
}

private fun planLabel(plan: Plan): String = when (plan) {
    Plan.FREE -> "Free"
    Plan.PREMIUM -> "Premium"
    Plan.PRO -> "Pro"
}

/** The small plan name shown on a user row. */
fun planText(plan: Plan): String = planLabel(plan)

/**
 * One dialog for adding a user, editing a user, and the "new users today" sequence.
 *
 * - Adding: pass [onSave] and [onDismiss] only (buttons: Cancel and Save).
 * - Editing: also pass [onDelete] (adds a Delete button that asks first).
 * - Sequence: pass [onSkip] and [onStop] (buttons: Save label, Skip and Stop).
 *
 * [otherUsers] are the users the email must not match (leave out the user being edited).
 * Callers should wrap this in key(...) when the same spot shows a different user next.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDialog(
    heading: String,
    initial: AppUser?,
    otherUsers: List<AppUser>,
    saveLabel: String = "Save",
    premiumAvailable: Boolean = true,
    proAvailable: Boolean = true,
    onSave: (name: String, email: String, phone: String, plan: Plan) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    onStop: (() -> Unit)? = null
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var email by rememberSaveable { mutableStateOf(initial?.email ?: "") }
    var phone by rememberSaveable { mutableStateOf(initial?.phone ?: "") }
    var planKey by rememberSaveable { mutableStateOf((initial?.plan ?: Plan.FREE).name) }
    var nameTouched by rememberSaveable { mutableStateOf(false) }
    var emailTouched by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val plan = runCatching { Plan.valueOf(planKey) }.getOrDefault(Plan.FREE)

    val nameTrim = name.trim()
    val emailTrim = email.trim()
    val nameOk = nameTrim.isNotEmpty()
    val emailFormatOk = isValidEmail(emailTrim)
    val duplicate = emailFormatOk &&
        otherUsers.any { it.email.trim().equals(emailTrim, ignoreCase = true) }
    val valid = nameOk && emailFormatOk && !duplicate

    val nameError = if (nameTouched && !nameOk) "Enter a name" else null
    val emailError = when {
        !emailTouched -> null
        !emailFormatOk -> "Enter a valid email"
        duplicate -> "This email is already in this app"
        else -> null
    }

    val save: () -> Unit = {
        if (valid) {
            onSave(nameTrim, emailTrim, phone.trim(), plan)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = heading,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(60)
                        nameTouched = true
                    },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = { if (nameError != null) Text(nameError) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it.take(100)
                        emailTouched = true
                    },
                    label = { Text("Email") },
                    singleLine = true,
                    isError = emailError != null,
                    supportingText = { if (emailError != null) Text(emailError) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { raw ->
                        phone = raw.filter { it.isDigit() || it in "+- ()" }.take(20)
                    },
                    label = { Text("Phone number (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Plan",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val choices = Plan.values().filter {
                    it == Plan.FREE ||
                        (it == Plan.PREMIUM && premiumAvailable) ||
                        (it == Plan.PRO && proAvailable) ||
                        it == initial?.plan
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (choice in choices) {
                        FilterChip(
                            selected = plan == choice,
                            onClick = { planKey = choice.name },
                            label = { Text(planLabel(choice)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (choices.size == 1) {
                    Text(
                        text = "Premium and Pro are not available in this app. " +
                            "Set their prices in Edit to turn them on.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (onSkip != null && onStop != null) {
                    // The "new users today" sequence.
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { save() },
                            enabled = valid,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(saveLabel) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onSkip,
                                modifier = Modifier.weight(1f)
                            ) { Text("Skip") }
                            OutlinedButton(
                                onClick = onStop,
                                modifier = Modifier.weight(1f)
                            ) { Text("Stop") }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onDelete != null) {
                            TextButton(onClick = { confirmDelete = true }) {
                                Text(
                                    text = "Delete",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        TextButton(onClick = { save() }, enabled = valid) { Text(saveLabel) }
                    }
                }
            }
        }
    }

    if (confirmDelete && onDelete != null) {
        ConfirmDialog(
            heading = "Delete ${initial?.name ?: "this user"}?",
            message = "This removes the user from the list. Income already recorded stays.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}
