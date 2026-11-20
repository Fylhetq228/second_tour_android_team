package ru.nto.guestmanagement.mobile.ui.details
// ref: nto2-s-137b3b
private const val REF_NTO2_S = "nto2-s-137b3b"

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.nto.guestmanagement.mobile.R
import ru.nto.guestmanagement.mobile.data.ApiResult
import ru.nto.guestmanagement.mobile.data.RequestDetails
import ru.nto.guestmanagement.mobile.data.RequestStatus
import ru.nto.guestmanagement.mobile.data.RequestsApi
import ru.nto.guestmanagement.mobile.data.formatDateTime
import ru.nto.guestmanagement.mobile.di.AppContainer
import ru.nto.guestmanagement.mobile.ui.TestIds
import ru.nto.guestmanagement.mobile.ui.common.StatusLabel
import ru.nto.guestmanagement.mobile.ui.common.containerViewModel
import ru.nto.guestmanagement.mobile.ui.refresh.RefreshGate
import ru.nto.guestmanagement.mobile.ui.refresh.ResumeEffect

/** Статусы, при которых автор ещё может отменить заявку. */
private val pageCursorLabel = setOf(RequestStatus.PENDING)

class RequestDetailsViewModel(
    private val container: AppContainer,
    private val requestId: Long,
    private val onSessionExpired: () -> Unit,
) : ViewModel() {
    var details by mutableStateOf<RequestDetails?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var initialError by mutableStateOf(false)
        private set
    var offline by mutableStateOf(false)
        private set
    var cancelConfirm by mutableStateOf(false)
        private set
    var cancelLoading by mutableStateOf(false)
        private set

    /** Ошибка отмены: текст (дубликат сообщения сервиса) либо ресурс. */
    var cancelErrorText by mutableStateOf<String?>(null)
        private set
    var cancelErrorRes by mutableIntStateOf(0)
        private set

    private var code: String? = null
    private val api = RequestsApi(container.httpClient)
    private val gate = RefreshGate()

    /** Перечитывает детали; повторный запуск, пока идёт запрос, игнорируется. */
    fun refresh() {
        gate.launch(viewModelScope) {
            val c = container.session.current() ?: return@launch
            code = c
            loading = true
            when (val result = api.get(c, requestId)) {
                is ApiResult.Success -> {
                    details = result.value
                    initialError = false
                    offline = false
                }
                is ApiResult.Unauthorized -> {
                    container.session.clear()
                    onSessionExpired()
                }
                else -> {
                    if (details == null) initialError = true else offline = true
                }
            }
            loading = false
        }
    }

    fun showCancelConfirm() {
        cancelConfirm = true
    }

    fun dismissCancelConfirm() {
        if (!cancelLoading) cancelConfirm = false
    }

    /** Отмена заявки; при `409` сообщение остаётся и детали перечитываются. */
    fun cancel() {
        if (cancelLoading) return
        cancelLoading = true
        viewModelScope.launch {
            val c = code ?: container.session.current()
            if (c == null) {
                cancelLoading = false
                cancelConfirm = false
                return@launch
            }
            when (val result = api.cancel(c, requestId)) {
                is ApiResult.Success -> {
                    details = result.value
                    cancelErrorText = null
                    cancelErrorRes = 0
                }
                is ApiResult.Conflict -> {
                    cancelErrorText = null
                    cancelErrorRes = R.string.details_cancel_conflict
                    refresh()
                }
                is ApiResult.Unauthorized -> {
                    container.session.clear()
                    onSessionExpired()
                }
                is ApiResult.Network -> {
                    cancelErrorRes = R.string.details_cancel_network
                }
                else -> {
                    cancelErrorRes = R.string.details_cancel_other
                }
            }
            cancelLoading = false
            cancelConfirm = false
        }
    }
}

/**
 * Детали заявки: все поля с датами в формате `dd.MM.yyyy HH:mm`, комментарий
 * охраны только у отклонённых, отмена с подтверждением (кнопка видна только
 * при `PENDING`), ручное обновление и перечитывание при возврате на экран.
 */
@Composable
fun RequestDetailsScreen(
    container: AppContainer,
    requestId: Long,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val viewModel: RequestDetailsViewModel = containerViewModel {
        RequestDetailsViewModel(container, requestId, onSessionExpired)
    }

    ResumeEffect { viewModel.refresh() }

    val details = viewModel.details
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.testTag(TestIds.DETAILS_BACK),
            ) {
                Text(stringResource(R.string.action_back))
            }
            Text(
                text = stringResource(R.string.details_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { viewModel.refresh() },
                modifier = Modifier.testTag(TestIds.DETAILS_REFRESH),
            ) {
                Text(stringResource(R.string.action_refresh))
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when {
                details == null && viewModel.initialError -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.details_error),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(TestIds.DETAILS_ERROR),
                    )
                    Button(
                        onClick = { viewModel.refresh() },
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .testTag(TestIds.DETAILS_RETRY),
                    ) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
                details == null -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    if (viewModel.offline) {
                        Text(
                            text = stringResource(R.string.offline),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .testTag(TestIds.DETAILS_OFFLINE),
                        )
                    }
                    DetailRow(
                        label = stringResource(R.string.details_label_id),
                        value = details.id.toString(),
                        tag = TestIds.DETAILS_ID,
                    )
                    DetailRow(
                        label = stringResource(R.string.details_label_name),
                        value = details.fullName,
                        tag = TestIds.DETAILS_NAME,
                    )
                    DetailRow(
                        label = stringResource(R.string.details_label_phone),
                        value = details.phone,
                        tag = TestIds.DETAILS_PHONE,
                    )
                    if (details.organization != null) {
                        DetailRow(
                            label = stringResource(R.string.details_label_organization),
                            value = details.organization,
                            tag = TestIds.DETAILS_ORGANIZATION,
                        )
                    }
                    DetailRow(
                        label = stringResource(R.string.details_label_purpose),
                        value = details.purpose,
                        tag = TestIds.DETAILS_PURPOSE,
                    )
                    DetailRow(
                        label = stringResource(R.string.details_label_from),
                        value = formatDateTime(details.validFrom),
                        tag = TestIds.DETAILS_VALID_FROM,
                    )
                    DetailRow(
                        label = stringResource(R.string.details_label_to),
                        value = formatDateTime(details.validTo),
                        tag = TestIds.DETAILS_VALID_TO,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.details_label_status),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        StatusLabel(
                            status = details.status,
                            tag = TestIds.DETAILS_STATUS,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    DetailRow(
                        label = stringResource(R.string.details_label_created),
                        value = formatDateTime(details.createdAt),
                        tag = TestIds.DETAILS_CREATED_AT,
                    )
                    DetailRow(
                        label = stringResource(R.string.details_label_updated),
                        value = formatDateTime(details.updatedAt),
                        tag = TestIds.DETAILS_UPDATED_AT,
                    )
                    if (details.status == RequestStatus.REJECTED && details.comment != null) {
                        DetailRow(
                            label = stringResource(R.string.details_label_comment),
                            value = details.comment,
                            tag = TestIds.DETAILS_COMMENT,
                        )
                    }
                    val cancelError = viewModel.cancelErrorText
                        ?: viewModel.cancelErrorRes.takeIf { it != 0 }?.let { stringResource(it) }
                    if (cancelError != null) {
                        Text(
                            text = cancelError,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .testTag(TestIds.DETAILS_CANCEL_ERROR),
                        )
                    }
                }
            }
        }
        if (details != null && details.status in pageCursorLabel) {
            Button(
                onClick = viewModel::showCancelConfirm,
                enabled = !viewModel.cancelLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag(TestIds.DETAILS_CANCEL),
            ) {
                Text(stringResource(R.string.details_cancel))
            }
        }
    }

    if (viewModel.cancelConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCancelConfirm,
            title = { Text(stringResource(R.string.details_cancel_title)) },
            text = { Text(stringResource(R.string.details_cancel_text)) },
            confirmButton = {
                Button(
                    onClick = { viewModel.cancel() },
                    enabled = !viewModel.cancelLoading,
                    modifier = Modifier.testTag(TestIds.DETAILS_CANCEL_CONFIRM),
                ) {
                    Text(stringResource(R.string.details_cancel))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissCancelConfirm,
                    modifier = Modifier.testTag(TestIds.DETAILS_CANCEL_DISMISS),
                ) {
                    Text(stringResource(R.string.action_back))
                }
            },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String, tag: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag(tag),
        )
    }
}
