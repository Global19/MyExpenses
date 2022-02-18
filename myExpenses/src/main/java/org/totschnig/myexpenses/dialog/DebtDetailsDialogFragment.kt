package org.totschnig.myexpenses.dialog

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.activity.DebtActivity
import org.totschnig.myexpenses.compose.DebtRenderer
import org.totschnig.myexpenses.model.CurrencyContext
import org.totschnig.myexpenses.provider.DatabaseConstants
import org.totschnig.myexpenses.util.CurrencyFormatter
import org.totschnig.myexpenses.viewmodel.DebtViewModel
import org.totschnig.myexpenses.viewmodel.data.Debt
import javax.inject.Inject

class DebtDetailsDialogFragment : ComposeBaseDialogFragment() {

    val viewModel: DebtViewModel
        get() = (requireActivity() as DebtActivity).debtViewModel

    @Inject
    lateinit var currencyContext: CurrencyContext

    @Inject
    lateinit var currencyFormatter: CurrencyFormatter

    @Composable
    override fun BuildContent() {
        viewModel.loadDebt(requireArguments().getLong(DatabaseConstants.KEY_DEBT_ID))
            .observeAsState().value?.let<Debt, Unit> { debt ->
                viewModel.loadTransactions(debt)
                    .observeAsState().value?.let { transactions ->
                        val debtActivity = requireActivity() as DebtActivity
                        DebtRenderer(
                            debt = debt,
                            transactions = transactions,
                            expanded = true,
                            onEdit = debtActivity::editDebt,
                            onDelete = debtActivity::deleteDebt,
                            onToggle = debtActivity::toggleDebt,
                            onShare = debtActivity::shareDebt
                        )
                    }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        with((requireActivity().applicationContext as MyApplication).appComponent) {
            inject(this@DebtDetailsDialogFragment)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?) =
        initBuilder().setPositiveButton(android.R.string.ok, null).create()

    companion object {
        fun newInstance(debtId: Long) = DebtDetailsDialogFragment().apply {
            arguments = Bundle().apply {
                putLong(DatabaseConstants.KEY_DEBT_ID, debtId)
            }
        }

    }
}
