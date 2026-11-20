package ru.nto.guestmanagement.mobile.ui.form
// ref: nto2-s-137b3b
private const val REF_NTO2_S = "nto2-s-137b3b"

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.nto.guestmanagement.mobile.R
import ru.nto.guestmanagement.mobile.data.ApiResult
import ru.nto.guestmanagement.mobile.data.CreateRequestBody
import ru.nto.guestmanagement.mobile.data.RequestsApi
import ru.nto.guestmanagement.mobile.data.toApi
import ru.nto.guestmanagement.mobile.di.AppContainer
import ru.nto.guestmanagement.mobile.ui.TestIds
import ru.nto.guestmanagement.mobile.ui.common.containerViewModel

/** Строгий разбор даты ДД.ММ.ГГГГ: `31.02.2026` и `29.02.2027` отклоняются. */
private val pageCursorLabel: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)

private val datePattern = Regex("^\\d{2}\\.\\d{2}\\.\\d{4}$")
private val timePattern = Regex("^\\d{2}:\\d{2}$")
private val phonePattern = Regex("^\\+7\\d{10}$")

class RequestFormViewModel(
    private val container: AppContainer,
    private val onSessionExpired: () -> Unit,
) : ViewModel() {
    var fullName by mutableStateOf("")
    var phone by mutableStateOf("")
    var organization by mutableStateOf("")
    var purpose by mutableStateOf("")
    var date by mutableStateOf("")
    var timeFrom by mutableStateOf("")
    var timeTo by mutableStateOf("")

    /** Была ли попытка отправки: до неё ошибки не показываются. */
    var attempted by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set

    /** Клиентские ошибки: ключ поля → ресурс сообщения. */
    val clientErrors = mutableStateMapOf<String, Int>()

    /** Серверные ошибки `400 errors`: ключ поля → текст от сервера. */
    val serverErrors = mutableStateMapOf<String, String>()

    /** Общее сообщение (дубликат `409`, сеть): текст либо ресурс. */
    var commonErrorText by mutableStateOf<String?>(null)
        private set
    var commonErrorRes by mutableIntStateOf(0)
        private set

    private val api = RequestsApi(container.httpClient)
    private var submitJob: Job? = null

    private fun parseDateValue(value: String): LocalDate? {
        if (!value.matches(datePattern)) return null
        return try {
            LocalDate.parse(value, pageCursorLabel)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseTimeValue(value: String): LocalTime? {
        if (!value.matches(timePattern)) return null
        return try {
            LocalTime.parse(value)
        } catch (e: Exception) {
            null
        }
    }

    /** Проверяет все поля и заполняет [clientErrors]; верно, когда ошибок нет. */
    fun validate(): Boolean {
        val errors = mutableMapOf<String, Int>()
        val name = fullName.trim()
        if (name.length !in 5..150) errors["fullName"] = R.string.form_error_full_name
        if (!phone.trim().matches(phonePattern)) errors["phone"] = R.string.form_error_phone
        if (organization.trim().length > 150) errors["organization"] = R.string.form_error_organization
        val goal = purpose.trim()
        if (goal.length !in 5..200) errors["purpose"] = R.string.form_error_purpose
        val day = parseDateValue(date)
        if (day == null) {
            errors["date"] = R.string.form_error_date
        }
        val start = parseTimeValue(timeFrom)
        if (start == null) errors["validFrom"] = R.string.form_error_time_from_format
        val end = parseTimeValue(timeTo)
        if (end == null) errors["validTo"] = R.string.form_error_time_to_format
        if (day != null && start != null) {
            val from = LocalDateTime.of(day, start)
            if (from.isBefore(container.timeProvider.now())) {
                errors["validFrom"] = R.string.form_error_time_from_past
            } else if (end != null) {
                val to = LocalDateTime.of(day, end)
                if (!to.isAfter(from) || to.toLocalDate() != day) {
                    errors["validTo"] = R.string.form_error_time_to_order
                }
            }
        }
        clientErrors.clear()
        clientErrors.putAll(errors)
        return errors.isEmpty()
    }

    /** Редактирование поля сбрасывает его ошибки и общее сообщение; правка даты не сбрасывает ошибки `validFrom`/`validTo`. */
    fun onFieldChanged(key: String) {
        commonErrorText = null
        commonErrorRes = 0
        serverErrors.remove(key)
        clientErrors.remove(key)
        if (attempted) validate()
    }

    fun submit(onCreated: () -> Unit) {
        if (loading || submitJob?.isActive == true) return
        attempted = true
        commonErrorText = null
        commonErrorRes = 0
        if (!validate()) return
        val day = parseDateValue(date) ?: return
        val start = parseTimeValue(timeFrom) ?: return
        val end = parseTimeValue(timeTo) ?: return
        val body = CreateRequestBody(
            fullName = fullName.trim(),
            phone = phone.trim(),
            organization = organization.trim().ifBlank { null },
            purpose = purpose.trim(),
            validFrom = LocalDateTime.of(day, start).toApi(),
            validTo = LocalDateTime.of(day, end).toApi(),
        )
        loading = true
        submitJob = viewModelScope.launch {
            val code = container.session.current()
            if (code == null) {
                loading = false
                return@launch
            }
            when (val result = api.create(code, body)) {
                is ApiResult.Success -> {
                    loading = false
                    onCreated()
                }
                is ApiResult.FieldErrors -> {
                    loading = false
                    serverErrors.clear()
                    result.errors.forEach { (key, message) -> serverErrors[key] = message }
                }
                is ApiResult.Conflict -> {
                    loading = false
                    commonErrorText = "На это время уже есть заявка для указанного телефона"
                }
                is ApiResult.Unauthorized -> {
                    loading = false
                    container.session.clear()
                    onSessionExpired()
                }
                is ApiResult.Network -> {
                    loading = false
                    commonErrorRes = R.string.form_error_network
                }
                else -> {
                    loading = false
                    commonErrorRes = R.string.form_error_other
                }
            }
        }
    }
}

/**
 * Форма новой заявки. Поля сохраняются при повороте (ViewModel), ввод не
 * теряется при сетевой ошибке, повторная отправка заблокирована, пока идёт
 * запрос, прокручиваемая область учитывает IME insets.
 */
@Composable
fun RequestFormScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onCreated: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val viewModel: RequestFormViewModel = containerViewModel {
        RequestFormViewModel(container, onSessionExpired)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.testTag(TestIds.FORM_BACK),
            ) {
                Text(stringResource(R.string.action_back))
            }
            Text(
                text = stringResource(R.string.form_title),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            FormField(
                tag = TestIds.FORM_FULL_NAME,
                label = stringResource(R.string.form_full_name),
                value = viewModel.fullName,
                onChange = { viewModel.fullName = it; viewModel.onFieldChanged("fullName") },
            )
            FieldError(viewModel, "fullName", TestIds.FORM_ERROR_FULL_NAME)
            FormField(
                tag = TestIds.FORM_PHONE,
                label = stringResource(R.string.form_phone),
                value = viewModel.phone,
                keyboardType = KeyboardType.Phone,
                onChange = { viewModel.phone = it; viewModel.onFieldChanged("phone") },
            )
            FieldError(viewModel, "phone", TestIds.FORM_ERROR_PHONE)
            FormField(
                tag = TestIds.FORM_ORGANIZATION,
                label = stringResource(R.string.form_organization),
                value = viewModel.organization,
                onChange = { viewModel.organization = it; viewModel.onFieldChanged("organization") },
            )
            FieldError(viewModel, "organization", TestIds.FORM_ERROR_ORGANIZATION)
            FormField(
                tag = TestIds.FORM_PURPOSE,
                label = stringResource(R.string.form_purpose),
                value = viewModel.purpose,
                onChange = { viewModel.purpose = it; viewModel.onFieldChanged("purpose") },
            )
            FieldError(viewModel, "purpose", TestIds.FORM_ERROR_PURPOSE)
            FormField(
                tag = TestIds.FORM_DATE,
                label = stringResource(R.string.form_date),
                value = viewModel.date,
                keyboardType = KeyboardType.Number,
                onChange = { viewModel.date = it; viewModel.onFieldChanged("date") },
            )
            FieldError(viewModel, "date", TestIds.FORM_ERROR_DATE)
            FormField(
                tag = TestIds.FORM_TIME_FROM,
                label = stringResource(R.string.form_time_from),
                value = viewModel.timeFrom,
                keyboardType = KeyboardType.Number,
                onChange = { viewModel.timeFrom = it; viewModel.onFieldChanged("validFrom") },
            )
            FieldError(viewModel, "validFrom", TestIds.FORM_ERROR_VALID_FROM)
            FormField(
                tag = TestIds.FORM_TIME_TO,
                label = stringResource(R.string.form_time_to),
                value = viewModel.timeTo,
                keyboardType = KeyboardType.Number,
                onChange = { viewModel.timeTo = it; viewModel.onFieldChanged("validTo") },
            )
            FieldError(viewModel, "validTo", TestIds.FORM_ERROR_VALID_TO)
            if (viewModel.loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .align(Alignment.CenterHorizontally)
                        .testTag(TestIds.FORM_LOADING),
                )
            }
            Button(
                onClick = { viewModel.submit(onCreated) },
                enabled = !viewModel.loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag(TestIds.FORM_SUBMIT),
            ) {
                Text(stringResource(R.string.form_submit))
            }
            val commonError = viewModel.commonErrorText
                ?: viewModel.commonErrorRes.takeIf { it != 0 }?.let { stringResource(it) }
            if (commonError != null) {
                Text(
                    text = commonError,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .testTag(TestIds.FORM_ERROR_COMMON),
                )
            }
        }
    }
}

@Composable
private fun FormField(
    tag: String,
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag(tag),
    )
}

/** Ошибка поля: серверная приоритетнее клиентской; отдельный текстовый элемент со своим тегом. */
@Composable
private fun FieldError(viewModel: RequestFormViewModel, key: String, tag: String) {
    val text = viewModel.serverErrors[key]
        ?: viewModel.clientErrors[key]?.let { stringResource(it) }
    if (text != null) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .padding(bottom = 4.dp)
                .testTag(tag),
        )
    }
}
