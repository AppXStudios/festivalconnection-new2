package com.appxstudios.festivalconnection.mesh.nostr

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class RelayStatus { CONNECTING, CONNECTED, DISCONNECTED, FAILED }

object NostrRelayManager {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val connections = ConcurrentHashMap<String, NostrRelayConnection>()
    private val subscriptions = ConcurrentHashMap<String, List<NostrFilter>>()
    private val eventCache = ConcurrentHashMap.newKeySet<String>()

    private val _connectedRelayCount = MutableStateFlow(0)
    val connectedRelayCount: StateFlow<Int> = _connectedRelayCount

    private val _relayStatuses = MutableStateFlow<Map<String, RelayStatus>>(emptyMap())
    val relayStatuses: StateFlow<Map<String, RelayStatus>> = _relayStatuses

    var onEvent: ((NostrEvent) -> Unit)? = null

    private val defaultRelays = listOf(
        "wss://relay.damus.io",
        "wss://relay.nostr.band",
        "wss://nos.lol",
        "wss://relay.snort.social",
        "wss://nostr.wine"
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect() {
        defaultRelays.forEach { connectToRelay(it) }
    }

    fun disconnect() {
        connections.values.forEach { it.disconnect() }
        connections.clear()
        _relayStatuses.value = emptyMap()
        _connectedRelayCount.value = 0
    }

    /**
     * Publish an event to all connected relays.
     * Returns the number of relays we sent to. Note: this is *send* count, not
     * delivery confirmation — full OK-ack tracking would require correlating
     * RelayMessage.Ok responses by event id, which we do not yet do.
     */
    fun publishEvent(event: NostrEvent): Int {
        val message = ClientMessage.Event(event).serialized()
        val targets = connections.values.filter { it.status == RelayStatus.CONNECTED }
        targets.forEach { it.send(message) }
        return targets.size
    }

    fun subscribe(filter: NostrFilter): String {
        val subId = java.util.UUID.randomUUID().toString().replace("-", "").take(16).lowercase()
        subscriptions[subId] = listOf(filter)

        val message = ClientMessage.Req(subId, listOf(filter)).serialized()
        connections.values.filter { it.status == RelayStatus.CONNECTED }.forEach { it.send(message) }
        return subId
    }

    fun unsubscribe(subscriptionId: String) {
        subscriptions.remove(subscriptionId)
        val message = ClientMessage.Close(subscriptionId).serialized()
        connections.values.filter { it.status == RelayStatus.CONNECTED }.forEach { it.send(message) }
    }

    private fun connectToRelay(url: String) {
        if (connections.containsKey(url)) return

        val conn = NostrRelayConnection(url, client,
            onMessage = { json -> handleRelayMessage(json, url) },
            onStatusChange = { status ->
                val statuses = _relayStatuses.value.toMutableMap()
                statuses[url] = status
                _relayStatuses.value = statuses
                _connectedRelayCount.value = connections.values.count { it.status == RelayStatus.CONNECTED }

                if (status == RelayStatus.CONNECTED) {
                    resendSubscriptions(url)
                }
            }
        )
        connections[url] = conn
        _relayStatuses.value = _relayStatuses.value.toMutableMap().apply { this[url] = RelayStatus.CONNECTING }
        conn.connect()
    }

    private fun resendSubscriptions(url: String) {
        val conn = connections[url] ?: return
        subscriptions.forEach { (subId, filters) ->
            conn.send(ClientMessage.Req(subId, filters).serialized())
        }
    }

    private fun handleRelayMessage(json: String, relay: String) {
        val message = RelayMessage.parse(json) ?: return

        when (message) {
            is RelayMessage.EventMsg -> {
                val event = message.event
                if (eventCache.contains(event.id)) return
                eventCache.add(event.id)
                if (!event.verifyId()) return
                onEvent?.invoke(event)
            }
            is RelayMessage.Ok -> {
                if (!message.accepted) println("[Nostr] Event ${message.eventId.take(8)} rejected: ${message.message}")
            }
            is RelayMessage.Eose -> println("[Nostr] EOSE: ${message.subscriptionId.take(8)}")
            is RelayMessage.Closed -> {
                println("[Nostr] Closed: ${message.subscriptionId.take(8)}")
                subscriptions.remove(message.subscriptionId)
            }
            is RelayMessage.Notice -> println("[Nostr] NOTICE: ${message.message}")
        }
    }
}

class NostrRelayConnection(
    private val url: String,
    private val client: OkHttpClient,
    private val onMessage: (String) -> Unit,
    private val onStatusChange: (RelayStatus) -> Unit
) {
    var status: RelayStatus = RelayStatus.DISCONNECTED
        private set

    private var webSocket: WebSocket? = null
    private var reconnectDelay = 2000L
    private val maxReconnectDelay = 120_000L
    private var shouldReconnect = true
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect() {
        shouldReconnect = true
        status = RelayStatus.CONNECTING
        onStatusChange(RelayStatus.CONNECTING)

        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                status = RelayStatus.CONNECTED
                reconnectDelay = 2000L
                onStatusChange(RelayStatus.CONNECTED)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                onMessage(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                handleDisconnection()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                handleDisconnection()
            }
        })
    }

    fun disconnect() {
        shouldReconnect = false
        webSocket?.close(1000, "Client disconnect")
        status = RelayStatus.DISCONNECTED
        onStatusChange(RelayStatus.DISCONNECTED)
    }

    fun send(message: String) {
        webSocket?.send(message)
    }

    private fun handleDisconnection() {
        status = RelayStatus.DISCONNECTED
        onStatusChange(RelayStatus.DISCONNECTED)

        if (!shouldReconnect) return

        val delay = reconnectDelay
        reconnectDelay = minOf(reconnectDelay * 2, maxReconnectDelay)

        scope.launch {
            delay(delay)
            connect()
        }
    }
}
