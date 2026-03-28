package app.aaps.core.objects.glucagon

import app.aaps.core.data.model.BS
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.utils.DateUtil
import kotlin.math.exp
import kotlin.math.ln

/**
 * Glucagon On Board (GOB) calculator.
 *
 * Applies a single-compartment exponential decay model, analogous to IOB:
 *   GOB(now) = Σ  dose_i_mcg × exp( −minutesSince_i × ln(2) / halfLifeMinutes )
 *
 * Queries BS records of type GLUCAGON from the last 3 hours.
 * Dose amounts are stored in the BS table in mg; this class converts to mcg internally.
 */
class GobCalculator(
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil
) {

    private companion object {
        const val LOOKBACK_MS = 3 * 60 * 60 * 1000L   // 3 hours
        val LN2 = ln(2.0)
    }

    /**
     * Calculate current Glucagon On Board in micrograms.
     * @param halfLifeMinutes decay half-life (typically 20 min)
     */
    fun currentGob(halfLifeMinutes: Int): Double {
        val now = dateUtil.now()
        val since = now - LOOKBACK_MS

        val doses = persistenceLayer
            .getBolusesFromTimeToTime(since, now, ascending = true)
            .filter { it.isValid && it.type == BS.Type.GLUCAGON }

        if (doses.isEmpty()) return 0.0

        return doses.sumOf { dose ->
            val doseMcg = dose.amount * 1000.0          // mg → mcg
            val minutesSince = (now - dose.timestamp) / 60_000.0
            doseMcg * exp(-minutesSince * LN2 / halfLifeMinutes)
        }
    }
}
