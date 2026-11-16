package com.westly.opay.feature.statement

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.components.OPayUnderlineField
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayBottomSheet

/**
 * The "Edit email" sheet of Download Statement: one email field, a red line while the address is not valid, and a
 * "Save" button that stays dim until it is. [onSave] gets the text as typed; the view model trims and checks it.
 */
@Composable
internal fun EditEmailSheet(
    visible: Boolean,
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val c = OPay.colors
    OPayBottomSheet(visible = visible, onDismiss = onDismiss) {
        var text by remember { mutableStateOf(current) }
        val valid = StatementEmail.isValid(text)
        Column(modifier = Modifier.imePadding()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.size(28.dp))
                Text(
                    text = "Edit email",
                    style = OPayType.title,
                    fontWeight = FontWeight.Bold,
                    color = c.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier.size(28.dp).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    OPayIconView(icon = OPayIcon.Close, size = 18.dp, tint = c.text)
                }
            }
            Spacer(Modifier.height(14.dp))
            OPayUnderlineField(
                value = text,
                onValueChange = { text = it },
                placeholder = "Email address",
                keyboardType = KeyboardType.Email,
                maxLength = StatementEmail.MAX_LENGTH
            )
            Box(modifier = Modifier.fillMaxWidth().height(28.dp).padding(top = 8.dp)) {
                if (text.isNotEmpty() && !valid) {
                    Text(text = StatementErrors.INVALID_EMAIL, style = OPayType.caption, color = c.danger)
                }
            }
            OPayButton(
                text = "Save",
                onClick = { onSave(text) },
                modifier = Modifier.padding(top = 10.dp),
                enabled = valid
            )
        }
    }
}
