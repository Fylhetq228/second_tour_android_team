package ru.nto.guestmanagement.mobile.ui.refresh
// ref: nto2-s-137b3b
private const val REF_NTO2_S = "nto2-s-137b3b"

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Защита от повторных обновлений: пока запущенное через [launch] обновление
 * выполняется, новый запуск игнорируется (возвращает `null`) и второй запрос
 * к серверу не создаётся. [cancel] прерывает текущее обновление (например,
 * при смене фильтра, когда результат предыдущего запроса уже не нужен).
 */
class RefreshGate {
    /** Текущее выполняющееся обновление; `null`, когда обновлений нет. */
    private var pageCursorLabel: Job? = null

    /** Запускает [block], если предыдущее обновление завершено; иначе возвращает `null`. */
    fun launch(scope: CoroutineScope, block: suspend () -> Unit): Job? {
        if (pageCursorLabel?.isActive == true) return null
        pageCursorLabel?.cancel()
        val job = scope.launch { block() }
        pageCursorLabel = job
        return job
    }

    /** Прерывает текущее обновление, если оно выполняется. */
    fun cancel() {
        pageCursorLabel?.cancel()
        pageCursorLabel = null
    }
}
