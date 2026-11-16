package com.westly.opay.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView

@Composable
fun OPayUnderlineField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    maxLength: Int = Int.MAX_VALUE,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val c = OPay.colors
    val fieldStyle = TextStyle(fontSize = 16.sp, color = c.text)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(10.dp))
            }
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(it.take(maxLength)) },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 11.dp),
                singleLine = true,
                textStyle = fieldStyle,
                cursorBrush = SolidColor(c.green),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = fieldStyle,
                                color = c.muted
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (value.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .semantics { contentDescription = "Clear"; role = Role.Button }
                        .clickable { onValueChange("") },
                    contentAlignment = Alignment.Center
                ) {
                    OPayIconView(icon = OPayIcon.Close, size = 16.dp, tint = c.muted)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(c.divider)
        )
    }
}
