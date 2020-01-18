package org.totschnig.myexpenses.viewmodel

import android.app.Application
import android.content.ContentUris
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import org.totschnig.myexpenses.model.*
import org.totschnig.myexpenses.model.Plan.CalendarIntegrationNotAvailableException
import org.totschnig.myexpenses.model.Transaction.ExternalStorageNotAvailableException
import org.totschnig.myexpenses.model.Transaction.UnknownPictureSaveException
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.provider.TransactionProvider.QUERY_PARAMETER_ACCOUNTY_TYPE_LIST
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.viewmodel.data.PaymentMethod
import java.util.*

const val ERROR_UNKNOWN = -1L
const val ERROR_EXTERNAL_STORAGE_NOT_AVAILABLE = -2L
const val ERROR_PICTURE_SAVE_UNKNOWN = -3L
const val ERROR_CALENDAR_INTEGRATION_NOT_AVAILABLE = -4L

class TransactionEditViewModel(application: Application) : ContentResolvingAndroidViewModel(application) {
    private val methods = MutableLiveData<List<PaymentMethod>>()

    fun getMethods(): LiveData<List<PaymentMethod>> {
        return methods
    }

    fun transaction(transactionId: Long, isTemplate: Boolean, clone: Boolean): LiveData<Transaction?> = liveData(context = coroutineContext()) {
        emit((if (isTemplate) Template.getInstanceFromDb(transactionId) else Transaction.getInstanceFromDb(transactionId)).also {
            it.prepareForEdit(clone)
        })
    }

    fun plan(planId: Long): LiveData<Plan?> = liveData(context = coroutineContext()) {
        emit(Plan.getInstanceFromDb(planId))
    }

    fun loadMethods(isIncome: Boolean, type: AccountType) {
        disposable = briteContentResolver.createQuery(TransactionProvider.METHODS_URI.buildUpon()
                .appendPath(TransactionProvider.URI_SEGMENT_TYPE_FILTER)
                .appendPath(if (isIncome) "1" else "-1")
                .appendQueryParameter(QUERY_PARAMETER_ACCOUNTY_TYPE_LIST, type.name)
                .build(), null, null, null, null, false)
                .mapToList { PaymentMethod.create(it) }
                .subscribe { methods.postValue(it) }
    }

    fun save(transaction: ITransaction): LiveData<Long> = liveData(context = coroutineContext()) {
        emit(
                try {
                    transaction.save(true)?.let { ContentUris.parseId(it) } ?: ERROR_UNKNOWN
                } catch (e: ExternalStorageNotAvailableException) {
                    ERROR_EXTERNAL_STORAGE_NOT_AVAILABLE
                } catch (e: UnknownPictureSaveException) {
                    val customData = HashMap<String, String>()
                    customData["pictureUri"] = e.pictureUri.toString()
                    customData["homeUri"] = e.homeUri.toString()
                    CrashHandler.report(e, customData)
                    ERROR_PICTURE_SAVE_UNKNOWN
                } catch (e: CalendarIntegrationNotAvailableException) {
                    ERROR_CALENDAR_INTEGRATION_NOT_AVAILABLE
                } catch (e: Exception) {
                    CrashHandler.report(e)
                    ERROR_UNKNOWN
                })
    }

    fun cleanupSplit(id: Long, isTemplate: Boolean): LiveData<Unit> = liveData(context = coroutineContext()) {
        emit(
                if (isTemplate) Template.cleanupCanceledEdit(id) else SplitTransaction.cleanupCanceledEdit(id)
        )
    }

    private fun coroutineContext() = viewModelScope.coroutineContext + Dispatchers.IO
}
