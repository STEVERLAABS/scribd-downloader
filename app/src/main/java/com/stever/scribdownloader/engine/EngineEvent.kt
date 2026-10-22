package com.stever.scribdownloader.engine

/** Events the JS autopilot reports through the SteVeRBridge (Task 7 vocabulary). */
sealed interface EngineEvent {
    data class Status(val step: Int, val key: String) : EngineEvent
    data object Captcha : EngineEvent
    data class PdfFound(val url: String) : EngineEvent
    data class Failed(val key: String) : EngineEvent
    data class TimedOut(val key: String) : EngineEvent
}
