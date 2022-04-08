package org.totschnig.myexpenses.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.viewmodel.data.Category2

@Composable
fun Budget(
    modifier: Modifier = Modifier,
    category: Category2,
    expansionMode: ExpansionMode,
    currency: CurrencyUnit,
    startPadding: Dp = 0.dp,
) {
    Column(
        modifier = modifier.then(
            if (category.level == 0) {
                (if (narrowScreen) {
                    Modifier.horizontalScroll(
                        rememberScrollState()
                    )
                } else Modifier)
                    .padding(horizontal = dimensionResource(id = R.dimen.activity_horizontal_margin))
            } else Modifier
        )
    ) {
        if (category.level > 0) {
            BudgetCategoryRenderer(
                category = category,
                currency = currency,
                expansionMode = expansionMode,
                startPadding = startPadding,
            )
            AnimatedVisibility(visible = expansionMode.isExpanded(category.id)) {
                Column(
                    verticalArrangement = Arrangement.Center
                ) {
                    category.children.forEach { model ->
                        Budget(
                            category = model,
                            expansionMode = expansionMode,
                            currency = currency,
                            startPadding = startPadding + 12.dp
                        )
                    }
                }
            }
        } else {
            Header()
            Divider(modifier = if (narrowScreen) Modifier.width(tableWidth) else Modifier)
            LazyColumn(
                verticalArrangement = Arrangement.Center
            ) {
                item {
                    Summary(category, currency)
                    Divider(modifier = if (narrowScreen) Modifier.width(tableWidth) else Modifier)
                }
                category.children.forEach { model ->
                    item {
                        Budget(
                            category = model,
                            expansionMode = expansionMode,
                            currency = currency
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Summary(category: Category2, currency: CurrencyUnit) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier
                .labelColumn(this),
            fontWeight = FontWeight.Bold,
            text = stringResource(id = R.string.menu_aggregates)
        )
        VerticalDivider()
        BudgetNumbers(category = category, currency = currency)
    }
}

@Composable
private fun VerticalDivider() {
    Divider(
        modifier = Modifier
            .height(48.dp)
            .width(1.dp)
    )
}

val breakPoint = 600.dp
const val labelFraction = 0.35f
const val numberFraction = 0.2f
val verticalDividerWidth = 1.dp
val tableWidth = breakPoint * (labelFraction + 3 * numberFraction) + verticalDividerWidth * 3

val narrowScreen: Boolean
    @Composable get() = LocalConfiguration.current.screenWidthDp < breakPoint.value

private fun Modifier.labelColumn(scope: RowScope): Modifier =
    composed { this.then(if (narrowScreen) width(breakPoint * 0.35f) else with(scope) { weight(2f) }) }.padding(end = 8.dp)

private fun Modifier.numberColumn(scope: RowScope): Modifier =
    composed { this.then(if (narrowScreen) width(breakPoint * 0.2f) else with(scope) { weight(1f) }) }
        .padding(horizontal = 8.dp)

@Composable
private fun Header() {
    @Composable
    fun RowScope.HeaderCell(stringRes: Int) {
        Text(
            modifier = Modifier
                .numberColumn(this),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            text = stringResource(id = stringRes)
        )
    }

    Row(modifier = Modifier.height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            modifier = Modifier
                .labelColumn(this)
        )
        VerticalDivider()
        HeaderCell(R.string.budget_table_header_allocated)
        VerticalDivider()
        HeaderCell(R.string.budget_table_header_spent)
        VerticalDivider()
        HeaderCell(R.string.budget_table_header_remainder)
    }
}

@Composable
private fun BudgetCategoryRenderer(
    category: Category2,
    currency: CurrencyUnit,
    expansionMode: ExpansionMode,
    startPadding: Dp,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .labelColumn(this)
                .padding(start = startPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isExpanded = expansionMode.isExpanded(category.id)
            Text(modifier = Modifier.weight(1f, false), text = category.label)
            if (category.children.isNotEmpty()) {
                IconButton(onClick = { expansionMode.toggle(category = category) }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(
                            id = if (isExpanded)
                                R.string.content_description_collapse else
                                R.string.content_description_expand
                        )
                    )
                }
            }
        }
        VerticalDivider()
        BudgetNumbers(category = category, currency = currency)
    }
}

@Composable
private fun RowScope.BudgetNumbers(category: Category2, currency: CurrencyUnit) {
    val allocation = if (category.children.isEmpty()) category.budget else category.children.sumOf { it.budget }
    if (allocation != category.budget) {
        Column(modifier = Modifier.numberColumn(this)) {
            Text(
                modifier = Modifier.clickable { }.fillMaxWidth(),
                text = LocalAmountFormatter.current(category.budget, currency),
                textAlign = TextAlign.End,
                textDecoration = TextDecoration.Underline
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = "(${LocalAmountFormatter.current(allocation, currency)})",
                textAlign = TextAlign.End
            )
        }
    } else {
        Text(
            modifier = Modifier.numberColumn(this).clickable { },
            text = LocalAmountFormatter.current(category.budget, currency),
            textAlign = TextAlign.End,
            textDecoration = TextDecoration.Underline
        )
    }
    VerticalDivider()
    Text(
        modifier = Modifier
            .numberColumn(this),
        text = LocalAmountFormatter.current(category.aggregateSum, currency),
        textAlign = TextAlign.End
    )
    VerticalDivider()
    Box(
        modifier = Modifier
            .numberColumn(this)
    ) {
        ColoredAmountText(
            modifier = Modifier.align(Alignment.CenterEnd),
            amount = category.budget + category.aggregateSum,
            currency = currency,
            textAlign = TextAlign.End,
            withBorder = true
        )
    }
}