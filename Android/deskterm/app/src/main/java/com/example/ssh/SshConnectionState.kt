package com.example.ssh

import com.example.data.model.SshHost

sealed interface SshConnectionState {
    data object Disconnected : SshConnectionState

    data class Connecting(
        val host: SshHost,
        val step: String = "Initiating connection..."
    ) : SshConnectionState

    data class Connected(
        val host: SshHost,
        val connectedSince: Long = System.currentTimeMillis()
    ) : SshConnectionState

    data class Error(
        val message: String,
        val canRetry: Boolean = true,
        val host: SshHost? = null
    ) : SshConnectionState
}

data class ConnectionMetrics(
    val bytesReceived: Long = 0,
    val bytesSent: Long = 0,
    val pingMs: Long = -1,
    val activePty: String = "xterm-256color"
)
