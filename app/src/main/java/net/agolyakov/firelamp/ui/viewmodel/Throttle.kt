package net.agolyakov.firelamp.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Rate-limits writes driven by a slider: at most one write per [intervalMs] for each key,
 * values arriving meanwhile are dropped except the latest one, which is always written.
 * (The lamp saves settings 3 s after the last change, so frequent writes do not wear its flash,
 * but the BLE queue would lag behind the finger.)
 */
class Throttle<K, V>(
    private val scope: CoroutineScope,
    private val intervalMs: Long = 100,
    private val write: suspend (K, V) -> Unit,
) {
    // A StateFlow per key: it keeps only the latest value (conflation) and, unlike a SharedFlow
    // without replay, does not lose a value submitted before its collector has started
    private val latest = HashMap<K, MutableStateFlow<V?>>()

    fun submit(key: K, value: V) {
        latest.getOrPut(key) {
            MutableStateFlow<V?>(null).also { flow ->
                scope.launch {
                    flow.filterNotNull().collect {
                        write(key, it)
                        delay(intervalMs)
                    }
                }
            }
        }.value = value
    }
}
