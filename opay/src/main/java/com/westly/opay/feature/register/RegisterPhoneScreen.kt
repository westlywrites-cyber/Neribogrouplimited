package com.westly.opay.feature.register

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.components.OPayUnderlineField
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.SecureKeypad
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.feature.settings.LegalTextOverlay
import com.westly.opay.feature.settings.privacyParagraphs
import com.westly.opay.feature.settings.termsParagraphs

private const val LEGAL_NONE = 0
private const val LEGAL_TERMS = 1
private const val LEGAL_PRIVACY = 2
private const val LINK_TAG = "legal"
private const val LINK_TERMS = "terms"
private const val LINK_PRIVACY = "privacy"
private val TOAST_ABOVE_KEYPAD = 340.dp

@Composable
fun RegisterPhoneScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onOpenCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val viewModel = rememberRegistrationViewModel()
    val state by viewModel.state.collectAsState()
    val toast = rememberOPayToast()
    var keypadOpen by rememberSaveable { mutableStateOf(true) }
    var legal by rememberSaveable { mutableStateOf(LEGAL_NONE) }
    var emailDraft by rememberSaveable { mutableStateOf(state.email) }
    var emailError by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RegistrationEvent.ShowToast -> toast.show(event.message)
                RegistrationEvent.OpenCode -> onOpenCode()
                is RegistrationEvent.GoTo -> Unit
            }
        }
    }
    BackHandler(enabled = state.busy) {}

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            OPayTopBar(
                title = "",
                onBack = onBack,
                modifier = Modifier.padding(horizontal = 8.dp),
                actionText = "Login",
                onAction = onLogin
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Enter your phone number",
                    style = OPayType.headline,
                    color = c.text
                )
                Text(
                    text = "We will send you a verification code",
                    style = OPayType.body,
                    color = c.muted,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(Modifier.height(24.dp))
                PhoneField(
                    digits = state.phoneDigits,
                    onClick = { keypadOpen = true },
                    onClear = viewModel::clearPhone
                )
                if (state.phoneError) {
                    Text(
                        text = "Please enter a valid phone number",
                        style = OPayType.caption,
                        color = c.danger,
                        modifier = Modifier.padding(start = 2.dp, top = 6.dp)
                    )
                }
                Spacer(Modifier.height(24.dp))
                OPayButton(
                    text = "Next",
                    onClick = viewModel::submitPhone,
                    enabled = state.canSubmitPhone
                )
                Spacer(Modifier.height(10.dp))
                TermsRow(
                    checked = state.termsAccepted,
                    onToggle = { viewModel.setTermsAccepted(!state.termsAccepted) },
                    onTerms = { legal = LEGAL_TERMS },
                    onPrivacy = { legal = LEGAL_PRIVACY }
                )
            }
            if (keypadOpen) {
                SecureKeypad(
                    onDigit = viewModel::onPhoneDigit,
                    onBackspace = viewModel::onPhoneBackspace,
                    onDone = { keypadOpen = false },
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        }
        if (state.busy) BusyOverlay()
        RegistrationDialogHost(
            dialog = state.dialog,
            onDismiss = viewModel::dismissDialog,
            onLogin = onLogin
        )
        OPayToastHost(state = toast, bottomPadding = TOAST_ABOVE_KEYPAD)
        if (legal != LEGAL_NONE) {
            LegalTextOverlay(
                title = if (legal == LEGAL_TERMS) "Terms of Use" else "Privacy Policy",
                paragraphs = if (legal == LEGAL_TERMS) termsParagraphs else privacyParagraphs,
                onClose = {
                    if (legal == LEGAL_PRIVACY) viewModel.saveEmail(emailDraft)
                    emailError = false
                    legal = LEGAL_NONE
                },
                footer = if (legal == LEGAL_PRIVACY) {
                    @Composable {
                        PrivacyEmailEntry(
                            value = emailDraft,
                            error = emailError,
                            onChange = {
                                emailDraft = it
                                emailError = false
                            },
                            onSave = {
                                if (viewModel.saveEmail(emailDraft)) {
                                    legal = LEGAL_NONE
                                } else {
                                    emailError = true
                                }
                            }
                        )
                    }
                } else {
                    null
                }
            )
        }
    }
}

/** The address field at the end of the Privacy Policy text. It is the only place an address is entered. */
@Composable
private fun PrivacyEmailEntry(
    value: String,
    error: Boolean,
    onChange: (String) -> Unit,
    onSave: () -> Unit
) {
    val c = OPay.colors
    Column(modifier = Modifier.padding(top = 10.dp, bottom = 24.dp)) {
        OPayUnderlineField(
            value = value,
            onValueChange = onChange,
            placeholder = "Email address",
            keyboardType = KeyboardType.Email,
            maxLength = EmailInput.MAX_LENGTH
        )
        if (error) {
            Text(
                text = "Please enter a valid email address",
                style = OPayType.caption,
                color = c.danger,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        OPayButton(text = "Save", onClick = onSave)
    }
}

@Composable
private fun PhoneField(digits: String, onClick: () -> Unit, onClear: () -> Unit) {
    val c = OPay.colors
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.card)
            .border(1.5.dp, c.green, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "+234",
            style = OPayType.titleSmall,
            fontWeight = FontWeight.Bold,
            color = c.text,
            modifier = Modifier
                .clip(OPayShapes.chip)
                .background(c.cardAlt)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (digits.isEmpty()) {
                Text(
                    text = "Enter your Mobile No.",
                    style = OPayType.titleSmall,
                    color = c.muted
                )
            } else {
                Text(
                    text = PhoneInput.formatGroups(digits),
                    style = OPayType.titleSmall,
                    color = c.text
                )
            }
        }
        if (digits.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .semantics {
                        contentDescription = "Clear"
                        role = Role.Button
                    }
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center
            ) {
                OPayIconView(icon = OPayIcon.Close, size = 16.dp, tint = c.muted)
            }
        }
    }
}

@Composable
private fun TermsRow(
    checked: Boolean,
    onToggle: () -> Unit,
    onTerms: () -> Unit,
    onPrivacy: () -> Unit
) {
    val c = OPay.colors
    val linkStyle = SpanStyle(color = c.green)
    val text = buildAnnotatedString {
        append("I have read and agree to the ")
        pushStringAnnotation(LINK_TAG, LINK_TERMS)
        withStyle(linkStyle) { append("Terms of Use") }
        pop()
        append(" and ")
        pushStringAnnotation(LINK_TAG, LINK_PRIVACY)
        withStyle(linkStyle) { append("Privacy Policy") }
        pop()
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .toggleable(
                    value = checked,
                    role = Role.Checkbox,
                    onValueChange = { onToggle() }
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (checked) c.green else Color.Transparent)
                    .border(1.5.dp, if (checked) c.green else c.muted, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (checked) {
                    OPayIconView(icon = OPayIcon.Check, size = 13.dp, tint = c.onGreen)
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = OPayType.caption,
            color = c.muted,
            onTextLayout = { layout = it },
            modifier = Modifier
                .weight(1f)
                .padding(top = 8.dp)
                .pointerInput(text) {
                    detectTapGestures { position ->
                        val result = layout ?: return@detectTapGestures
                        val offset = result.getOffsetForPosition(position)
                        val link = text.getStringAnnotations(LINK_TAG, offset, offset).firstOrNull()
                        when (link?.item) {
                            LINK_TERMS -> onTerms()
                            LINK_PRIVACY -> onPrivacy()
                            else -> onToggle()
                        }
                    }
                }
        )
    }
}
