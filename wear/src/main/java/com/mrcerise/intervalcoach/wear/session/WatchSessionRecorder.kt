package com.mrcerise.intervalcoach.wear.session

import com.mrcerise.intervalcoach.wear.history.WatchHistoryRecorder
import com.pulse.sync.LiveSession
import com.pulse.sync.SessionLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Where a finished watch session goes: into the watch's own history, **and** on to the phone.
 *
 * The two are independent on purpose. The history is local and always written, because a watch on
 * its own must still be able to say what it did; the link decides for itself whether it can
 * deliver, queues what it cannot, and is a no-op when the phone link is off. A watch with no phone
 * therefore behaves exactly like a watch with a phone that happens to be absent — the difference
 * is only in whether the record leaves the wrist.
 *
 * Independence has to be enforced, not just intended: this runs on the engine's event callback at the
 * end of a session, so a publisher that throws (no peer stack, a link that has been torn down with the
 * watch still finishing its last step) would otherwise unwind into the session teardown after the
 * session was already recorded. Both calls are therefore contained here, and the order stays
 * "history first, link second" so the local record never depends on anything the phone is doing.
 */
class WatchSessionRecorder(
    private val scope: CoroutineScope,
    private val history: WatchHistoryRecorder,
    private val link: WatchSessionPublisher,
) : WatchSessionPublisher {

    override fun onLiveSnapshot(live: LiveSession) {
        runCatching { link.onLiveSnapshot(live) }
    }

    override fun onSessionFinished(log: SessionLog) {
        // Fire-and-forget on the container scope, like the queue write: neither may block the
        // engine's event callback.
        scope.launch {
            try {
                history.record(log)
            } catch (cancellation: CancellationException) {
                // A cancellation is the scope ending, not a failure to report: rethrow it, or this
                // coroutine would finish "successfully" while its parent is coming down.
                throw cancellation
            } catch (_: Exception) {
                // A history write that fails must not take the link's copy of the session with it.
            }
        }
        runCatching { link.onSessionFinished(log) }
    }
}
