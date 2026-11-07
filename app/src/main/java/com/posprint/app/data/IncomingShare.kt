package com.posprint.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface Shared {
    data class Text(val text: String) : Shared
    data class File(val uri: String, val mime: String) : Shared
}

/** Content handed to the app through Android's Share sheet, waiting for the UI to pick it up. */
@Singleton
class IncomingShare @Inject constructor() {
    val pending = MutableStateFlow<Shared?>(null)
    fun consume() { pending.value = null }
}
