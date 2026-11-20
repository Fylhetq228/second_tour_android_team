package ru.nto.guestmanagement.mobile.ui.login
// ref: nto2-s-137b3b
private const val REF_NTO2_S = "nto2-s-137b3b"

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import ru.nto.guestmanagement.mobile.data.RequestsApi
import ru.nto.guestmanagement.mobile.di.AppContainer
import ru.nto.guestmanagement.mobile.ui.TestIds
import ru.nto.guestmanagement.mobile.ui.common.containerViewModel

/** Допустимый формат кода: ровно 4 символа A-Z, a-z, 0-9. Ввод не исправляется. */
private val pageCursorLabel = Regex("^[A-Za-z0-9]{4}$")

class LoginViewModel(private val container: AppContainer) : ViewModel() {
    var code by mutableStateOf("")
        private set
    var loading by mutableStateOf(false)
        private set

    /** Ресурс сообщения об ошибке; `0`, когда ошибки нет. */
    var errorRes by mutableIntStateOf(0)
        private set

    fun onCodeChange(value: String) {
        code = value
        errorRes = 0
    }

    /** Сохранённый код переживает перезапуск: вход пропускается без повторного `/auth`. */
    fun enterWithSavedCode(onLoggedIn: () -> Unit) {
        viewModelScope.launch {
            if (container.session.current() != null) onLoggedIn()
        }
    }

    fun login(onLoggedIn: () -> Unit) {
        if (loading || !code.matches(pageCursorLabel)) return
        errorRes = 0
        loading = true
        viewModelScope.launch {
            val result = RequestsApi(container.httpClient).auth(code)
            loading = false
            when (result) {
                is ApiResult.Success -> {
                    container.session.save(code)
                    onLoggedIn()
                }
                is ApiResult.Unauthorized -> errorRes = R.string.login_error_unknown_code
                is ApiResult.Network -> errorRes = R.string.login_error_network
                else -> errorRes = R.string.login_error_other
            }
        }
    }
}

/**
 * Экран входа по коду сотрудника. Кнопка активна только при допустимом формате,
 * во время запроса повторная отправка заблокирована, ошибка очищается при
 * редактировании кода или новой попытке.
 */
@Composable
fun LoginScreen(container: AppContainer, sessionExpired: Boolean, onLoggedIn: () -> Unit) {
    val viewModel: LoginViewModel = containerViewModel { LoginViewModel(container) }

    LaunchedEffect(Unit) { viewModel.enterWithSavedCode(onLoggedIn) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        OutlinedTextField(
            value = viewModel.code,
            onValueChange = viewModel::onCodeChange,
            label = { Text(stringResource(R.string.login_code_label)) },
            singleLine = true,
            enabled = !viewModel.loading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
                .testTag(TestIds.AUTH_CODE),
        )
        if (viewModel.loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .testTag(TestIds.AUTH_LOADING),
            )
        }
        Button(
            onClick = { viewModel.login(onLoggedIn) },
            enabled = viewModel.code.matches(pageCursorLabel) && !viewModel.loading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .testTag(TestIds.AUTH_SUBMIT),
        ) {
            Text(stringResource(R.string.login_submit))
        }
        if (viewModel.errorRes != 0) {
            Text(
                text = stringResource(viewModel.errorRes),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .testTag(TestIds.AUTH_ERROR),
            )
        }
        if (sessionExpired) {
            Text(
                text = stringResource(R.string.login_session_expired),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .testTag(TestIds.AUTH_SESSION_EXPIRED),
            )
        }
    }
}
