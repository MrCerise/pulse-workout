package com.pulse.intervalcoach.wear.history

import com.pulse.sync.SessionLog
import com.pulse.sync.SessionStatus
import com.pulse.sync.SyncJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * One finished session, as the watch remembers it.
 *
 * This is deliberately *not* the full [SessionLog]: the log exists to be delivered to the phone
 * (with its event trail) and belongs to the queue, while this is what a wrist can show — what ran,
 * when, for how long, and whether it finished. Keeping the two apart means the history can be kept
 * for a long time without a watch-sized DataStore holding thousands of events.
 */
@Serializable
data class WatchSessionRecord(
    val sessionId: String,
    val planName: String,
    val startedAt: Long,
    val endedAt: Long,
    val activeMillis: Long,
    val completedIntervals: Int,
    val totalIntervals: Int,
    val skippedIntervals: Int,
    val stoppedEarly: Boolean,
) {

    companion object {
        fun from(log: SessionLog): WatchSessionRecord = WatchSessionRecord(
            sessionId = log.sessionId,
            planName = log.planName,
            startedAt = log.startedAt,
            endedAt = log.endedAt,
            activeMillis = log.activeMillis,
            completedIntervals = log.completedIntervals,
            totalIntervals = log.totalIntervals,
            skippedIntervals = log.skippedIntervals,
            stoppedEarly = log.status != SessionStatus.COMPLETED,
        )
    }
}

/** How many sessions a watch keeps: a wrist is a logbook, not an archive. */
const val WATCH_HISTORY_LIMIT = 20

/**
 * Newest first, one entry per session id, capped at [limit]. Pure, so the rule is provable in a
 * JVM test instead of being buried in a DataStore edit.
 */
fun mergeWatchHistory(
    current: List<WatchSessionRecord>,
    record: WatchSessionRecord,
    limit: Int = WATCH_HISTORY_LIMIT,
): List<WatchSessionRecord> =
    (listOf(record) + current.filterNot { it.sessionId == record.sessionId }).take(limit)

/** JSON codec for the history list, sharing the protocol's JSON configuration. */
object WatchHistoryCodec {

    fun encode(records: List<WatchSessionRecord>): String =
        SyncJson.messages.encodeToString(ListSerializer(WatchSessionRecord.serializer()), records)

    /** Anything unreadable decodes to nothing: a corrupt history must never block the app. */
    fun decode(text: String?): List<WatchSessionRecord> {
        if (text.isNullOrBlank()) return emptyList()
        return runCatching {
            SyncJson.messages.decodeFromString(ListSerializer(WatchSessionRecord.serializer()), text)
        }
            .getOrDefault(emptyList())
            .take(WATCH_HISTORY_LIMIT)
    }
}

/** What the recorder needs from the history; [WatchHistoryStore] is the real implementation. */
interface WatchHistoryRecorder {
    suspend fun record(log: SessionLog)
}
