package org.totschnig.myexpenses.delegate

import android.database.Cursor
import android.os.Bundle
import android.text.Editable
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.SimpleCursorAdapter
import icepick.State
import kotlinx.android.synthetic.main.exchange_rate_row.view.*
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.ExpenseEdit
import org.totschnig.myexpenses.contract.TransactionsContract
import org.totschnig.myexpenses.databinding.DateEditBinding
import org.totschnig.myexpenses.databinding.OneExpenseBinding
import org.totschnig.myexpenses.model.*
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.provider.DatabaseConstants
import org.totschnig.myexpenses.ui.AmountInput
import org.totschnig.myexpenses.ui.ExchangeRateEdit
import org.totschnig.myexpenses.ui.MyTextWatcher
import org.totschnig.myexpenses.ui.SpinnerHelper
import org.totschnig.myexpenses.util.FilterCursorWrapper
import java.math.BigDecimal
import java.util.*

class TransferDelegate(viewBinding: OneExpenseBinding, dateEditBinding: DateEditBinding, prefHandler: PrefHandler, isTemplate: Boolean) :
        TransactionDelegate<ITransfer>(viewBinding, dateEditBinding, prefHandler, isTemplate) {
    override val operationType = TransactionsContract.Transactions.TYPE_TRANSFER

    private val lastExchangeRateRelevantInputs = intArrayOf(INPUT_EXCHANGE_RATE, INPUT_AMOUNT)
    private lateinit var transferAccountSpinner: SpinnerHelper
    private lateinit var transferAccountsAdapter: SimpleCursorAdapter
    @JvmField
    @State
    var mTransferAccountId: Long? = null
    var transferPeer: Long? = null
    private lateinit var mTransferAccountCursor: FilterCursorWrapper

    override val helpVariant: ExpenseEdit.HelpVariant
        get() = when {
            isTemplate -> ExpenseEdit.HelpVariant.templateTransfer
            isSplitPart -> ExpenseEdit.HelpVariant.transfer
            else -> ExpenseEdit.HelpVariant.splitPartTransfer
        }
    override val title
        get() = context.getString(if (parentId == null) R.string.menu_edit_transfer else R.string.menu_edit_split_part_transfer)
    override val typeResId = R.string.split_transaction


    override fun bind(transaction: ITransfer, isCalendarPermissionPermanentlyDeclined: Boolean, newInstance: Boolean, savedInstance: Boolean, recurrence: Plan.Recurrence?, plan: Plan?) {
        mTransferAccountId = transaction.transferAccountId
        transferPeer = transaction.transferPeer
        transferAccountSpinner = SpinnerHelper(viewBinding.TransferAccount)
        viewBinding.Amount.addTextChangedListener(LinkedTransferAmountTextWatcher(true))
        viewBinding.TransferAmount.addTextChangedListener(LinkedTransferAmountTextWatcher(false))
        viewBinding.ERR.ExchangeRate.setExchangeRateWatcher(LinkedExchangeRateTextWatcher())
        viewBinding.Amount.hideTypeButton()
        viewBinding.CategoryRow.visibility = View.GONE
        viewBinding.TransferAccountRow.visibility = View.VISIBLE
        viewBinding.AccountLabel.setText(R.string.transfer_from_account)
        super.bind(transaction, isCalendarPermissionPermanentlyDeclined, newInstance, savedInstance, recurrence, plan)
        hideRowsSpecificToMain()
        if (transaction.id != 0L) {
            configureTransferDirection()
        }
    }

    override fun populateFields(transaction: ITransfer, prefHandler: PrefHandler, newInstance: Boolean) {
        super.populateFields(transaction, prefHandler, newInstance)
        transaction.transferAmount?.let {
            viewBinding.TransferAmount.setAmount(it.amountMajor.abs())
        }

    }

    override fun createAdapters(newInstance: Boolean, transaction: ITransaction) {
        createAccountAdapter()
        createStatusAdapter(transaction)
        if (newInstance) {
            createOperationTypeAdapter()
        }
        createTransferAccountAdapter()
    }

    override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        super.onItemSelected(parent, view, position, id)
        when (parent.id) {
            R.id.TransferAccount -> {
                mTransferAccountId = transferAccountSpinner.selectedItemId
                configureTransferInput()
            }
        }
    }

    override fun setAccounts(data: Cursor, currencyExtra: String?) {
        super.setAccounts(data, currencyExtra)
        mTransferAccountCursor = FilterCursorWrapper(data)
        val selectedPosition = setTransferAccountFilterMap()
        transferAccountsAdapter.swapCursor(mTransferAccountCursor)
        transferAccountSpinner.setSelection(selectedPosition)
        mTransferAccountId = transferAccountSpinner.selectedItemId
        configureTransferInput()
        if (!isTemplate) {
             isProcessingLinkedAmountInputs = true
             updateExchangeRates(viewBinding.TransferAmount)
             isProcessingLinkedAmountInputs = false
        }
    }

    private fun setTransferAccountFilterMap(): Int {
        val fromAccount = mAccounts[accountSpinner.selectedItemPosition]
        val list = ArrayList<Int>()
        var position = 0
        var selectedPosition = 0
        for (i in mAccounts.indices) {
            if (fromAccount.id != mAccounts[i].id) {
                list.add(i)
                if (mTransferAccountId != null && mTransferAccountId == mAccounts[i].id) {
                    selectedPosition = position
                }
                position++
            }
        }
        mTransferAccountCursor.setFilterMap(list)
        transferAccountsAdapter.notifyDataSetChanged()
        return selectedPosition
    }

    private fun transferAccount() = getAccountFromSpinner(transferAccountSpinner)

    private fun configureTransferInput() {
        val transferAccount = transferAccount()
        val currentAccount = currentAccount()
        if (transferAccount == null || currentAccount == null) {
            return
        }
        val currency = currentAccount.currencyUnit
        val transferAccountCurrencyUnit = transferAccount.currencyUnit
        val isSame = currency == transferAccountCurrencyUnit
        setVisibility(viewBinding.TransferAmountRow, !isSame)
        setVisibility(viewBinding.ERR.root as ViewGroup, !isSame /*&& mTransaction !is Template*/)
        addCurrencyToInput(viewBinding.TransferAmountLabel, viewBinding.TransferAmount, transferAccountCurrencyUnit.symbol(), R.string.amount)
        viewBinding.TransferAmount.setFractionDigits(transferAccountCurrencyUnit.fractionDigits())
        viewBinding.ERR.ExchangeRate.setCurrencies(currency, transferAccountCurrencyUnit)
        //TODO check history of this dead code
        val bundle = Bundle(2)
        bundle.putStringArray(DatabaseConstants.KEY_CURRENCY, arrayOf(currency.code(), transferAccountCurrencyUnit.code()))
    }

    private fun createTransferAccountAdapter() {
        transferAccountsAdapter = SimpleCursorAdapter(context, android.R.layout.simple_spinner_item, null, arrayOf(DatabaseConstants.KEY_LABEL), intArrayOf(android.R.id.text1), 0)
        transferAccountsAdapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item)
        transferAccountSpinner.adapter = transferAccountsAdapter
        transferAccountSpinner.setOnItemSelectedListener(this)
    }

    fun configureTransferDirection() {
        if (isIncome) {
            switchAccountViews()
        }
    }

    fun switchAccountViews() {
        val accountSpinner = accountSpinner.spinner
        val transferAccountSpinner = transferAccountSpinner.spinner
        with(viewBinding.Table) {
            removeView(viewBinding.AmountRow)
            removeView(viewBinding.TransferAmountRow)
            if (isIncome) {
                if (accountSpinner.parent === viewBinding.AccountRow && transferAccountSpinner.parent === viewBinding.TransferAccountRow) {
                    viewBinding.AccountRow.removeView(accountSpinner)
                    viewBinding.TransferAccountRow.removeView(transferAccountSpinner)
                    viewBinding.AccountRow.addView(transferAccountSpinner)
                    viewBinding.TransferAccountRow.addView(accountSpinner)
                }
                addView(viewBinding.TransferAmountRow, 2)
                addView(viewBinding.AmountRow, 4)
            } else {
                if (accountSpinner.parent === viewBinding.TransferAccountRow && transferAccountSpinner.parent === viewBinding.AccountRow) {
                    viewBinding.AccountRow.removeView(transferAccountSpinner)
                    viewBinding.TransferAccountRow.removeView(accountSpinner)
                    viewBinding.AccountRow.addView(accountSpinner)
                    viewBinding.TransferAccountRow.addView(transferAccountSpinner)
                }
                addView(viewBinding.AmountRow, 2)
                addView(viewBinding.TransferAmountRow, 4)
            }
        }

        linkAccountLabels()
    }

    override fun linkAccountLabels() {
        with(host) {
            linkInputWithLabel(accountSpinner.spinner,
                    if (isIncome) viewBinding.TransferAccountLabel else viewBinding.AccountLabel)
            linkInputWithLabel(transferAccountSpinner.spinner,
                    if (isIncome) viewBinding.AccountLabel else viewBinding.TransferAccountLabel)
        }
    }

    private inner class LinkedTransferAmountTextWatcher(
            /**
             * true if we are linked to from amount
             */
            var isMain: Boolean) : MyTextWatcher() {

        override fun afterTextChanged(s: Editable) {
            if (isProcessingLinkedAmountInputs) return
            isProcessingLinkedAmountInputs = true
            if (isTemplate) {
                (if (isMain) viewBinding.TransferAmount else viewBinding.Amount).clear()
            } else if (viewBinding.ERR.root.visibility == View.VISIBLE) {
                val currentFocus = if (isMain) INPUT_AMOUNT else INPUT_TRANSFER_AMOUNT
                if (lastExchangeRateRelevantInputs[0] != currentFocus) {
                    lastExchangeRateRelevantInputs[1] = lastExchangeRateRelevantInputs[0]
                    lastExchangeRateRelevantInputs[0] = currentFocus
                }
                if (lastExchangeRateRelevantInputs[1] == INPUT_EXCHANGE_RATE) {
                    applyExchangeRate(if (isMain) viewBinding.Amount else viewBinding.TransferAmount,
                            if (isMain) viewBinding.TransferAmount else viewBinding.Amount,
                            viewBinding.ERR.root.ExchangeRate.getRate(!isMain))
                } else {
                    updateExchangeRates(viewBinding.TransferAmount)
                }
            }
            isProcessingLinkedAmountInputs = false
        }
    }

    private fun applyExchangeRate(from: AmountInput, to: AmountInput, rate: BigDecimal?) {
        val input = validateAmountInput(from, false)
        to.setAmount(if (rate != null && input != null) input.multiply(rate) else BigDecimal(0), false)
    }

    private fun updateExchangeRates(other: AmountInput) {
        val amount = validateAmountInput(viewBinding.Amount, false)
        val transferAmount = validateAmountInput(other, false)
        viewBinding.ERR.root.ExchangeRate.calculateAndSetRate(amount, transferAmount)
    }

    override fun buildTransaction(forSave: Boolean, currencyContext: CurrencyContext, accountId: Long): ITransfer? {
        val amount = validateAmountInput(forSave)
        val currentAccount = currentAccount()!!
        val transferAccount = transferAccount()!!
        val isSame = currentAccount.currencyUnit == transferAccount.currencyUnit
        val transferAmount: BigDecimal?
        if (isSame && amount != null) {
            transferAmount = amount.negate()
        } else {
            transferAmount = validateAmountInput(viewBinding.TransferAmount, forSave)?.let {
                if (isIncome) it.negate() else it
            }
        }
        return if (isTemplate) {
            if (amount == null && transferAmount == null) {
                return null
            }
            buildTemplate(accountId).apply {
                if (amount != null) {
                    this.amount = Money(currentAccount.currencyUnit, amount)
                } else if (!isSame && transferAmount != null) {
                    this.accountId = transferAccount.id
                    setTransferAccountId(currentAccount.id)
                    this.amount = Money(transferAccount.currencyUnit, transferAmount)
                    viewBinding.Amount.setError(null)
                }
            }
        } else {
            if (amount == null || transferAmount == null) {
                return null
            }
            Transfer(accountId, transferAccount.id, parentId).apply {
                transferPeer = this@TransferDelegate.transferPeer
                setAmountAndTransferAmount(
                        Money(currentAccount.currencyUnit, amount),
                        Money(transferAccount.currencyUnit, transferAmount))
            }
        }
    }

    override fun updateAccount(account: Account) {
        super.updateAccount(account)
        transferAccountSpinner.setSelection(setTransferAccountFilterMap())
        mTransferAccountId = transferAccountSpinner.selectedItemId
        configureTransferInput()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val transferAccountId = transferAccountSpinner.selectedItemId
        if (transferAccountId != AdapterView.INVALID_ROW_ID) {
            mTransferAccountId = transferAccountId
            super.onSaveInstanceState(outState)
        }
    }

    fun invert() {
        viewBinding.Amount.toggle()
        switchAccountViews()
    }

    private inner class LinkedExchangeRateTextWatcher : ExchangeRateEdit.ExchangeRateWatcher {
        override fun afterExchangeRateChanged(rate: BigDecimal, inverse: BigDecimal) {
            if (isProcessingLinkedAmountInputs) return
            isProcessingLinkedAmountInputs = true
            val constant: AmountInput?
            val variable: AmountInput?
            val exchangeFactor: BigDecimal
            if (lastExchangeRateRelevantInputs[0] != INPUT_EXCHANGE_RATE) {
                lastExchangeRateRelevantInputs[1] = lastExchangeRateRelevantInputs[0]
                lastExchangeRateRelevantInputs[0] = INPUT_EXCHANGE_RATE
            }
            if (lastExchangeRateRelevantInputs[1] == INPUT_AMOUNT) {
                constant = viewBinding.Amount
                variable = viewBinding.TransferAmount
                exchangeFactor = rate
            } else {
                constant = viewBinding.TransferAmount
                variable = viewBinding.Amount
                exchangeFactor = inverse
            }
            applyExchangeRate(constant, variable, exchangeFactor)
            isProcessingLinkedAmountInputs = false
        }
    }

    companion object {
        private const val INPUT_EXCHANGE_RATE = 1
        private const val INPUT_AMOUNT = 2
        private const val INPUT_TRANSFER_AMOUNT = 3
    }

}