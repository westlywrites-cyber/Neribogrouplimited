package com.westly.opay.feature.register

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayTopBar
import java.time.LocalDate

private val MONTH_NAMES = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

private fun displayDate(iso: String): String {
    val date = PepRules.parseDate(iso) ?: return ""
    return date.dayOfMonth.toString() + " " + MONTH_NAMES[date.monthValue - 1] + " " + date.year
}

/** Registration step 2 of 2, fourth page: office holder and relative questions (reference B10 and B11). */
@Composable
fun RegisterPepDetailsScreen(
    onBack: () -> Unit,
    onHelp: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val context = LocalContext.current
    val viewModel = rememberRegistrationViewModel()
    val form by viewModel.pep.collectAsState()
    var officeSheet by rememberSaveable { mutableStateOf(false) }
    val today = remember { LocalDate.now() }

    fun openDatePicker() {
        val base = PepRules.parseDate(form.startDate) ?: today
        val dialog = DatePickerDialog(
            context,
            { _, year, month, day ->
                viewModel.setPepStartDate(LocalDate.of(year, month + 1, day).toString())
            },
            base.year,
            base.monthValue - 1,
            base.dayOfMonth
        )
        dialog.datePicker.maxDate = System.currentTimeMillis()
        dialog.show()
    }

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
                actionText = "Help",
                onAction = onHelp
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                OPayCard {
                    KycRadioQuestion(
                        question = "Are you a current or past political office holder?",
                        value = form.holder,
                        onChange = viewModel::setPepHolder,
                        yesLabel = "Yes, I am",
                        noLabel = "No, I'm not",
                        yesFirst = false
                    )
                    if (form.holder) {
                        Spacer(Modifier.height(6.dp))
                        KycChoiceField(
                            value = form.office,
                            placeholder = "Select political office",
                            onClick = { officeSheet = true },
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        KycChoiceField(
                            value = displayDate(form.startDate),
                            placeholder = "Public Office Start Date",
                            onClick = { openDatePicker() },
                            modifier = Modifier.padding(top = 10.dp)
                        )
                        KycCheckRow(
                            checked = form.holdsPosition,
                            text = "I currently hold a political position",
                            onToggle = { viewModel.setPepHoldsPosition(!form.holdsPosition) }
                        )
                    }
                }
                OPayCard {
                    KycRadioQuestion(
                        question = "Are you related to a current or past political office holder?",
                        value = form.related,
                        onChange = viewModel::setPepRelated,
                        yesLabel = "Yes, I am",
                        noLabel = "No, I'm not",
                        yesFirst = false
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                OPayButton(
                    text = "Next",
                    onClick = onNext,
                    enabled = form.detailsValid(today)
                )
            }
        }

        KycChoiceSheet(
            visible = officeSheet,
            title = "Select political office",
            options = PEP_OFFICES,
            selected = form.office,
            onPick = {
                viewModel.setPepOffice(it)
                officeSheet = false
            },
            onDismiss = { officeSheet = false }
        )
    }
}
