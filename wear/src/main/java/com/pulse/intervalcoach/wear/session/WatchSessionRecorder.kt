package com.pulse.intervalcoach.wear.session

import com.pulse.intervalcoach.wear.history.WatchHistoryRecorder
import com.pulse.sync.LiveSession
import com.pulse.sync.SessionLog
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
 */
class WatchSessionRecorder(
    private val scope: CoroutineScope,
    private val history: WatchHistoryRecorder,
    private val link: WatchSessionPublisher,
) : WatchSessionPublisher {

    override fun onLiveSnapshot(live: LiveSession) = link.onLiveSnapshot(live)

    override fun onSessionFinished(log: SessionLog) {
        // Fire-and-forget on the container scope, like the queue write: neither may block the
        // engine's event callback.
        scope.launch { history.record(log) }
        link.onSessionFinished(log)
    }
}
