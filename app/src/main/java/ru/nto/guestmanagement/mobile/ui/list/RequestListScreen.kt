package ru.nto.guestmanagement.mobile.ui.list
// ref: nto2-s-137b3b
private const val REF_NTO2_S = "nto2-s-137b3b"

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.nto.guestmanagement.mobile.R
import ru.nto.guestmanagement.mobile.data.ApiResult
import ru.nto.guestmanagement.mobile.data.RequestStatus
import ru.nto.guestmanagement.mobile.data.RequestSummary
import ru.nto.guestmanagement.mobile.data.RequestsApi
import ru.nto.guestmanagement.mobile.data.formatDate
import ru.nto.guestmanagement.mobile.data.formatInterval
import ru.nto.guestmanagement.mobile.di.AppContainer
import ru.nto.guestmanagement.mobile.ui.TestIds
import ru.nto.guestmanagement.mobile.ui.common.StatusLabel
import ru.nto.guestmanagement.mobile.ui.common.containerViewModel
import ru.nto.guestmanagement.mobile.ui.refresh.RefreshGate
import ru.nto.guestmanagement.mobile.ui.refresh.ResumeEffect

/** Пять фильтров списка: `null` — все заявки (без параметра `status`). */
private val pageCursorLabel: List<Triple<RequestStatus?, String, Int>> = listOf(
    Triple(null, TestIds.REQUESTS_FILTER_ALL, R.string.filter_all),
    Triple(RequestStatus.PENDING, TestIds.REQUESTS_FILTER_PENDING, R.string.filter_pending),
    Triple(RequestStatus.APPROVED, TestIds.REQUESTS_FILTER_APPROVED, R.string.filter_approved),
    Triple(RequestStatus.REJECTED, TestIds.REQUESTS_FILTER_REJECTED, R.string.filter_rejected),
    Triple(RequestStatus.CANCELLED, TestIds.REQUESTS_FILTER_CANCELLED, R.string.filter_cancelled),
)

class RequestListViewModel(
    private val container: AppContainer,
    private val onSessionExpired: () -> Unit,
) : ViewModel() {
    var filter by mutableStateOf<RequestStatus?>(null)
        private set
    var items by mutableStateOf<List<RequestSummary>?>(null)
        private set
    var initialError by mutableStateOf(false)
        private set
    var offline by mutableStateOf(false)
        private set

    private val api = RequestsApi(container.httpClient)
    private val gate = RefreshGate()

    /** Перечитывает список с сервера; повторный запуск, пока идёт запрос, игнорируется. */
    fun refresh() {
        gate.launch(viewModelScope) {
            val code = container.session.current() ?: return@launch
            when (val result = api.list(code, filter)) {
                is ApiResult.Success -> {
                    items = result.value
                    initialError = false
                    offline = false
                }
                is ApiResult.Unauthorized -> {
                    container.session.clear()
                    onSessionExpired()
                }
                else -> {
                    if (items == null) initialError = true else offline = true
                }
            }
        }
    }

    /** Смена фильтра прерывает текущий запрос и сразу запрашивает список заново. */
    fun changeFilter(status: RequestStatus?) {
        if (status == filter) return
        filter = status
        gate.cancel()
        refresh()
    }

    fun logout(onLogout: () -> Unit) {
        viewModelScope.launch {
            container.session.clear()
            onLogout()
        }
    }
}

/**
 * Список заявок сотрудника: фильтры по статусам, карточки в порядке ответа API,
 * пустое состояние, первоначальная ошибка, «Нет связи» при сбое повторной
 * загрузки, ручное обновление и перечитывание при каждом возврате на экран.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RequestListScreen(
    container: AppContainer,
    onOpenDetails: (Long) -> Unit,
    onAddRequest: () -> Unit,
    onLogout: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val viewModel: RequestListViewModel = containerViewModel {
        RequestListViewModel(container, onSessionExpired)
    }

    ResumeEffect { viewModel.refresh() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.list_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { viewModel.logout(onLogout) },
                modifier = Modifier.testTag(TestIds.REQUESTS_LOGOUT),
            ) {
                Text(stringResource(R.string.action_logout))
            }
            TextButton(
                onClick = { viewModel.refresh() },
                modifier = Modifier.testTag(TestIds.REQUESTS_REFRESH),
            ) {
                Text(stringResource(R.string.action_refresh))
            }
        }
        Button(
            onClick = onAddRequest,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag(TestIds.REQUESTS_ADD),
        ) {
            Text(stringResource(R.string.action_add))
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            pageCursorLabel.forEach { (status, tag, label) ->
                FilterChip(
                    selected = viewModel.filter == status,
                    onClick = { viewModel.changeFilter(status) },
                    label = { Text(stringResource(label)) },
                    modifier = Modifier.testTag(tag),
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val items = viewModel.items
            when {
                items == null && viewModel.initialError -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.list_error),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(TestIds.REQUESTS_ERROR),
                    )
                }
                items == null -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    if (viewModel.offline) {
                        Text(
                            text = stringResource(R.string.offline),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .testTag(TestIds.REQUESTS_OFFLINE),
                        )
                    }
                    if (items.isEmpty()) {
                        Text(
                            text = stringResource(R.string.list_empty),
                            modifier = Modifier
                                .padding(16.dp)
                                .testTag(TestIds.REQUESTS_EMPTY),
                        )
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(items) { index, request ->
                            RequestCard(index, request, onOpenDetails)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Карточка заявки. Открытие деталей — через `pointerInput`, а не `clickable`:
 * так семантика потомков (теги ФИО, даты, времени, статуса) не объединяется
 * с карточкой и остаётся видна тестам.
 */
@Composable
private fun RequestCard(index: Int, request: RequestSummary, onOpenDetails: (Long) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .pointerInput(request.id) {
                detectTapGestures(onTap = { onOpenDetails(request.id) })
            }
            .testTag(TestIds.requestsPos(index)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = request.fullName,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.testTag(TestIds.REQUEST_NAME),
            )
            Row(modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = formatDate(request.validFrom),
                    modifier = Modifier.testTag(TestIds.REQUEST_DATE),
                )
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                Text(
                    text = formatInterval(request.validFrom, request.validTo),
                    modifier = Modifier.testTag(TestIds.REQUEST_TIME),
                )
            }
            StatusLabel(
                status = request.status,
                tag = TestIds.REQUEST_STATUS,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
