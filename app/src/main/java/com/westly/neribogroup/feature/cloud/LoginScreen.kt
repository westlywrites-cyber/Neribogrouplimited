package com.westly.neribogroup.feature.cloud

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.cloud.CloudConfig
import com.westly.neribogroup.feature.common.ConfirmDialog
import com.westly.neribogroup.feature.common.FlatCard
import com.westly.neribogroup.feature.common.NeriboDialog
import com.westly.neribogroup.feature.common.TintedButton
import com.westly.neribogroup.feature.common.neriboFieldColors

/** Which question about the cloud data is open on the login screen. */
enum class LinkDialog { NONE, USE_CLOUD, CASE_C, REPLACE_CONFIRM }

/**
 * Sign in to the cloud (no sign-up, no "forgot password") and watch the first link.
 * Shown only when the owner taps Connect to cloud in Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    initialUrl: String,
    initialAnonKey: String,
    initialEmail: String,
    /** The URL of an already initialized link, or "" when there is none (used to warn about a new URL). */
    linkedUrl: String,
    signedIn: Boolean,
    working: Boolean,
    errorMessage: String,
    linkStep: Int,
    linkIsDownload: Boolean,
    linkMessage: String,
    linkOk: Boolean,
    dialog: LinkDialog,
    onSignIn: (url: String, anonKey: String, email: String, password: String, resetLink: Boolean) -> Unit,
    onTryAgain: () -> Unit,
    onBack: () -> Unit,
    onUseCloud: () -> Unit,
    onKeepPhone: () -> Unit,
    onAskReplace: () -> Unit,
    onBackToChoice: () -> Unit,
    onConfirmReplace: () -> Unit
) {
    BackHandler { if (!working) onBack() }

    var url by remember { mutableStateOf(initialUrl) }
    var key by remember { mutableStateOf(initialAnonKey) }
    var email by remember { mutableStateOf(initialEmail) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var keyFocused by remember { mutableStateOf(false) }
    var confirmUrlChange by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect to your cloud", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { if (!working) onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Your data is copied to your private cloud so it is safe and Claude can help run the company. " +
                    "You can still use the app with no internet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!signedIn) {
                LoginField(
                    label = "Project URL",
                    value = url,
                    onChange = { url = it },
                    keyboardType = KeyboardType.Uri,
                    helper = "from Supabase → Project Settings → API",
                    enabled = !working
                )
                LoginField(
                    label = "Anon key",
                    value = key,
                    onChange = { key = it },
                    keyboardType = KeyboardType.Password,
                    visualTransformation = if (keyFocused) VisualTransformation.None else AnonKeyMask,
                    onFocusChange = { keyFocused = it },
                    enabled = !working
                )
                LoginField(
                    label = "Email",
                    value = email,
                    onChange = { email = it },
                    keyboardType = KeyboardType.Email,
                    enabled = !working
                )
                LoginField(
                    label = "Password",
                    value = password,
                    onChange = { password = it },
                    keyboardType = KeyboardType.Password,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    enabled = !working,
                    action = {
                        TextButton(onClick = { showPassword = !showPassword }) {
                            Text(if (showPassword) "Hide" else "Show")
                        }
                    }
                )

                val shownError = if (localError.isNotEmpty()) localError else errorMessage
                if (shownError.isNotEmpty()) {
                    Text(
                        text = shownError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TintedButton(
                        text = if (working) "Signing in…" else "Sign in",
                        enabled = !working,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val clean = CloudConfig.normalizeUrl(url)
                            when {
                                clean == null || !clean.contains('.') ->
                                    localError = "That URL does not look right. It should start with https://"
                                key.trim().length <= 20 ->
                                    localError = "The anon key does not look right."
                                !email.contains('@') ->
                                    localError = "Enter the email of your account."
                                password.isEmpty() ->
                                    localError = "Enter your password."
                                else -> {
                                    localError = ""
                                    if (linkedUrl.isNotEmpty() && linkedUrl != clean) {
                                        confirmUrlChange = true
                                    } else {
                                        onSignIn(url, key, email, password, false)
                                    }
                                }
                            }
                        }
                    )
                    if (working) {
                        Spacer(modifier = Modifier.width(12.dp))
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            } else {
                LinkProgress(
                    working = working,
                    step = linkStep,
                    isDownload = linkIsDownload,
                    message = linkMessage,
                    ok = linkOk,
                    onTryAgain = onTryAgain,
                    onDone = onBack
                )
            }
        }
    }

    if (confirmUrlChange) {
        ConfirmDialog(
            heading = "New cloud address",
            message = "This phone was linked to another address. If you continue, this phone is linked again " +
                "from the start. Your data on the phone is kept and a safety copy already exists.",
            confirmLabel = "Continue",
            onConfirm = {
                confirmUrlChange = false
                onSignIn(url, key, email, password, true)
            },
            onDismiss = { confirmUrlChange = false },
            destructive = false
        )
    }

    when (dialog) {
        LinkDialog.NONE -> {}
        LinkDialog.USE_CLOUD -> NeriboDialog(
            onDismiss = {},
            title = "Your cloud already has data"
        ) {
            Text(
                text = "Your cloud already has data. Use the cloud data on this phone? " +
                    "Your current phone data is saved as a file first.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
            TintedButton(text = "Use cloud data", onClick = onUseCloud, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onKeepPhone, modifier = Modifier.fillMaxWidth()) {
                Text("Keep phone data (do not link)")
            }
        }
        LinkDialog.CASE_C -> NeriboDialog(
            onDismiss = {},
            title = "Unfinished data in the cloud"
        ) {
            Text(
                text = "The cloud already has some data from an unfinished upload. What should happen?",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
            TintedButton(
                text = "Replace the cloud with this phone",
                onClick = onAskReplace,
                modifier = Modifier.fillMaxWidth(),
                destructive = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            TintedButton(text = "Use the cloud data", onClick = onUseCloud, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onKeepPhone, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
        LinkDialog.REPLACE_CONFIRM -> {
            var typed by remember { mutableStateOf("") }
            NeriboDialog(
                onDismiss = {},
                title = "Replace the cloud?"
            ) {
                Text(
                    text = "The data in the cloud will be overwritten with the data on this phone. " +
                        "Type REPLACE to continue.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                LoginField(
                    label = "Type the word REPLACE",
                    value = typed,
                    onChange = { typed = it },
                    keyboardType = KeyboardType.Text
                )
                Spacer(modifier = Modifier.height(16.dp))
                TintedButton(
                    text = "Replace the cloud",
                    onClick = onConfirmReplace,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = typed == "REPLACE",
                    destructive = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onBackToChoice, modifier = Modifier.fillMaxWidth()) { Text("Go back") }
            }
        }
    }
}

/** The 4 progress lines, the working notice, the result and the Try again button. */
@Composable
private fun LinkProgress(
    working: Boolean,
    step: Int,
    isDownload: Boolean,
    message: String,
    ok: Boolean,
    onTryAgain: () -> Unit,
    onDone: () -> Unit
) {
    val labels = listOf(
        "Making a safety copy…",
        if (isDownload) "Downloading…" else "Uploading…",
        "Checking…",
        "Done ✔"
    )
    FlatCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((index, label) in labels.withIndex()) {
                val finished = ok || step > index
                val current = !ok && working && step == index
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (finished) "✔" else if (current) "…" else "○",
                        modifier = Modifier.width(28.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (finished) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
    if (working) {
        Text(
            text = "Working… do not close the app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else if (ok) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        TintedButton(text = "Done", onClick = onDone, modifier = Modifier.fillMaxWidth())
    } else {
        Text(
            text = if (message.isNotEmpty()) message else "The link did not finish. Your data on the phone is safe.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        TintedButton(text = "Try again", onClick = onTryAgain, modifier = Modifier.fillMaxWidth())
    }
}

/** A labelled one-line text field in the app's field style. [action] sits at the right of the label. */
@Composable
private fun LoginField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    helper: String? = null,
    enabled: Boolean = true,
    onFocusChange: (Boolean) -> Unit = {},
    action: (@Composable () -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (action != null) action()
        }
        Spacer(modifier = Modifier.height(4.dp))
        TextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { onFocusChange(it.isFocused) },
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(14.dp),
            colors = neriboFieldColors()
        )
        if (helper != null) {
            Text(
                text = helper,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Shows only the first 6 characters of the anon key when the field is not being edited. */
private object AnonKeyMask : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        val shown = if (s.length <= 6) s else s.take(6) + "•".repeat(s.length - 6)
        return TransformedText(AnnotatedString(shown), OffsetMapping.Identity)
    }
}
