package ru.srr.vpscockpit

object TunnelEvents {
    const val ACTION_STATUS = "ru.srr.vpscockpit.TUNNEL_STATUS"
    const val EXTRA_STATE = "state"
    const val EXTRA_MESSAGE = "message"

    const val STATE_CONNECTING = "connecting"
    const val STATE_READY = "ready"
    const val STATE_FAILED = "failed"
    const val STATE_STOPPED = "stopped"
}
