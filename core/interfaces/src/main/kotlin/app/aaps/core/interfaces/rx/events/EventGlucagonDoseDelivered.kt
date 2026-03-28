package app.aaps.core.interfaces.rx.events

/**
 * Fired after a glucagon dose delivery attempt.
 * @param doseMcg dose that was requested, in micrograms
 * @param success true if delivery was initiated successfully
 */
data class EventGlucagonDoseDelivered(val doseMcg: Double, val success: Boolean) : Event()
