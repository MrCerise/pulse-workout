package com.mrcerise.intervalcoach.audio

/** Pure queue bookkeeping; SpeechCoach serializes access with its own monitor. */
internal class UtteranceQueue {
    private val pending = linkedMapOf<String, Long>()

    fun submit(id: String, estimatedMillis: Long, flush: Boolean): Long {
        if (flush) pending.clear()
        pending[id] = estimatedMillis.coerceAtLeast(0L)
        return pending.values.sum()
    }

    /** Only a known, last pending ID can release the duck. Stale/duplicate callbacks do nothing. */
    fun finish(id: String?): Boolean = pending.remove(id) != null && pending.isEmpty()

    fun clear() = pending.clear()
}
