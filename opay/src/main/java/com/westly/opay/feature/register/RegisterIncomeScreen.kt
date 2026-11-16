package com.westly.opay.feature.register

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.overlay.OPayDialog
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.rememberOPayToast

private const val SHEET_NONE = 0
private const val SHEET_OCCUPATION = 1
private const val SHEET_INCOME = 2
private const val SHEET_SOURCE = 3
private const val SHEET_EXPECTED = 4

private const val INVALID_ANSWERS_TEXT = "Please check your answers and try again."

/**
 * Registration step 2 of 2, last page: occupation, yearly income and other income (reference B12 to B14).
 * "Next" sends the whole form to the backend and then opens the page the registration state points to.
 */
@Composable
fun RegisterIncomeScreen(
    onBack: () -> Unit,
    onHelp: () -> Unit,
    onContinue: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val viewModel = rememberRegistrationViewModel()
    val form by viewModel.income.collectAsState()
    val ui by viewModel.questionnaire.collectAsState()
    val toast = rememberOPayToast()
    var sheet by rememberSaveable { mutableIntStateOf(SHEET_NONE) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RegistrationEvent.ShowToast -> toast.show(event.message)
                is RegistrationEvent.GoTo -> onContinue(event.route)
                RegistrationEvent.OpenCode -> Unit
            }
        }
    }
    BackHandler(enabled = ui.submitting) {}

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
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
                Text(
                    text = "Source of Income",
                    style = OPayType.headline,
                    color = c.text
                )
                Text(
                    text = "Step 2 of 2",
                    style = OPayType.caption,
                    color = c.muted,
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                )
                KycPill("Provide details of your source of income")
                OPayCard {
                    Text(
                        text = "What is your occupation?",
                        style = OPayType.titleSmall,
                        color = c.text
                    )
                    KycChoiceField(
                        value = form.occupation,
                        placeholder = "Select your occupation",
                        onClick = { sheet = SHEET_OCCUPATION },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                OPayCard {
                    Text(
                        text = "How much do you earn yearly?",
                        style = OPayType.titleSmall,
                        color = c.text
                    )
                    KycChoiceField(
                        value = form.incomeRange,
                        placeholder = "Select your yearly income",
                        onClick = { sheet = SHEET_INCOME },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                OPayCard {
                    KycRadioQuestion(
                        question = "Do you have other sources of income?",
                        value = form.otherIncome,
                        onChange = viewModel::setOtherIncome,
                        yesLabel = "Yes",
                        noLabel = "No",
                        yesFirst = true
                    )
                    if (form.otherIncome == true) {
                        Text(
                            text = "Other source of income",
                            style = OPayType.caption,
                            color = c.muted,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        KycChoiceField(
                            value = form.otherSource,
                            placeholder = "Select your other source of income",
                            onClick = { sheet = SHEET_SOURCE },
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            text = "What is your Expected Annual Income?",
                            style = OPayType.caption,
                            color = c.muted,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        KycChoiceField(
                            value = form.expectedAnnual,
                            placeholder = "Select your annual income",
                            onClick = { sheet = SHEET_EXPECTED },
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
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
                    onClick = { viewModel.submitQuestionnaire() },
                    enabled = form.isValid && !ui.submitting
                )
            }
        }

        LocationSheet(
            visible = sheet == SHEET_OCCUPATION,
            items = OCCUPATIONS,
            selected = form.occupation,
            onPick = {
                viewModel.setOccupation(it)
                sheet = SHEET_NONE
            },
            onDismiss = { sheet = SHEET_NONE }
        )
        KycChoiceSheet(
            visible = sheet == SHEET_INCOME,
            title = "Select yearly income range",
            options = INCOME_RANGES,
            selected = form.incomeRange,
            onPick = {
                viewModel.setIncomeRange(it)
                sheet = SHEET_NONE
            },
            onDismiss = { sheet = SHEET_NONE }
        )
        KycChoiceSheet(
            visible = sheet == SHEET_SOURCE,
            title = "Select your other source of income",
            options = OTHER_INCOME_SOURCES,
            selected = form.otherSource,
            onPick = {
                viewModel.setOtherSource(it)
                sheet = SHEET_NONE
            },
            onDismiss = { sheet = SHEET_NONE }
        )
        KycChoiceSheet(
            visible = sheet == SHEET_EXPECTED,
            title = "Select your expected annual income",
            options = EXPECTED_ANNUAL_INCOMES,
            selected = form.expectedAnnual,
            onPick = {
                viewModel.setExpectedAnnual(it)
                sheet = SHEET_NONE
            },
            onDismiss = { sheet = SHEET_NONE }
        )

        if (ui.invalidAnswers) {
            OPayDialog(
                title = INVALID_ANSWERS_TEXT,
                message = null,
                primaryText = "OK",
                onPrimary = viewModel::dismissInvalidAnswers,
                onDismiss = viewModel::dismissInvalidAnswers
            )
        }
        OPayToastHost(state = toast)
        if (ui.submitting) BusyOverlay()
    }
}
