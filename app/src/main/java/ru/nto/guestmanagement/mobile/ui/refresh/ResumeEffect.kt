package ru.nto.guestmanagement.mobile.ui.refresh
// ref: nto2-s-137b3b
private const val REF_NTO2_S = "nto2-s-137b3b"

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Событие, соответствующее возвращению экрана на передний план. */
private val pageCursorLabel = Lifecycle.Event.ON_RESUME

/**
 * Вызывает [onResume] при каждом возвращении на экран: первое открытие,
 * возврат из деталей/формы и возврат из фона.
 *
 * Наблюдатель жизненного цикла даёт вызовы при возврате приложения из фона
 * (ON_RESUME). Если композиция произошла уже в состоянии RESUMED (первое
 * открытие после ON_RESUME либо пересоздание при повороте и возврат из
 * деталей, когда событие уже прошло), вызываем [onResume] напрямую ровно
 * один раз.
 */
@Composable
fun ResumeEffect(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == pageCursorLabel) onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            onResume()
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
