package com.anaya.app.service

import com.anaya.app.ml.ParsedTransaction
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentEventBus @Inject constructor() {

    private val _events = MutableSharedFlow<ParsedTransaction>(extraBufferCapacity = 10)
    val events = _events.asSharedFlow()

    fun emit(parsed: ParsedTransaction) {
        _events.tryEmit(parsed)
    }
}
