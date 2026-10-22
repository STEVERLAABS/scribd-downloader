package com.stever.scribdownloader.ui

import androidx.annotation.StringRes
import com.stever.scribdownloader.R

/** statusKey (JS vocabulary, Task 7) -> localized string resource. */
@StringRes
fun statusStringRes(key: String?): Int = when (key) {
    "init" -> R.string.status_init
    "verify" -> R.string.status_verify
    "verify_manual" -> R.string.status_verify_manual
    "verify_done" -> R.string.status_verify_done
    "wait" -> R.string.status_wait
    "redirect" -> R.string.status_redirect
    "analyzing" -> R.string.status_analyzing
    "validating" -> R.string.status_validating
    "success" -> R.string.status_success
    "downloading" -> R.string.status_downloading
    else -> R.string.status_init
}

/** Error key -> localized, precise message (no fake success — spec anti-goal). */
@StringRes
fun errorStringRes(key: String?): Int = when (key) {
    "error_invalid_url" -> R.string.error_invalid_url
    "error_network" -> R.string.error_network
    "error_detected" -> R.string.error_detected
    "error_damaged" -> R.string.error_damaged
    "error_timeout" -> R.string.error_timeout
    "error_captcha_timeout" -> R.string.error_captcha_timeout
    "error_cancelled" -> R.string.error_cancelled
    else -> R.string.error_network
}
