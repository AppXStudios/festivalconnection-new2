package com.appxstudios.festivalconnection.mesh.nostr

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// Central dispatcher for Nostr events — multiple screens can observe via SharedFlow
// without overwriting NostrRelayManager.onEvent (a single shared callback).
object NostrEventDispatcher {
    private val _events = MutableSharedFlow<NostrEvent>(
        replay = 0,
        extraBufferCapacity = 100
    )
    val events: SharedFlow<NostrEvent> = _events.asSharedFlow()

    fun install() {
        // Replace any existing onEvent handler and fan out to all observers
        NostrRelayManager.onEvent = { event ->
            _events.tryEmit(event)
        }
    }

    fun emit(event: NostrEvent) {
        _events.tryEmit(event)
    }
}
