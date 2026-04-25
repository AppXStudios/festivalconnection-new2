package com.appxstudios.festivalconnection.mesh.nostr

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// Central dispatcher for Nostr events — multiple screens can observe via SharedFlow
// without overwriting NostrRelayManager.onEvent (a single shared callback).
object NostrEventDispatcher {
    // BufferOverflow.DROP_OLDEST keeps the newest events when the buffer fills.
    // The previous default (SUSPEND with tryEmit) silently dropped *new* events
    // when full, so a burst of incoming relay messages would be invisible to UI
    // collectors. tryEmit always returns true with DROP_OLDEST, so the warning
    // log below is a defensive no-op kept in case the policy is changed back.
    private val _events = MutableSharedFlow<NostrEvent>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<NostrEvent> = _events.asSharedFlow()

    fun install() {
        // Replace any existing onEvent handler and fan out to all observers
        NostrRelayManager.onEvent = { event ->
            val emitted = _events.tryEmit(event)
            if (!emitted) {
                android.util.Log.w(
                    "NostrEventDispatcher",
                    "Buffer full, dropped event ${event.id.take(16)}"
                )
            }
        }
    }

    fun emit(event: NostrEvent) {
        _events.tryEmit(event)
    }
}
