package app.aaps.core.objects.glucagon

import app.aaps.core.data.model.BS
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever
import kotlin.math.exp
import kotlin.math.ln

/**
 * Unit tests for [GobCalculator].
 *
 * Verifies that the exponential decay model produces correct GOB values
 * for known dose histories.
 */
class GobCalculatorTest : TestBase() {

    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var dateUtil: DateUtil

    private lateinit var gobCalculator: GobCalculator

    private val now = 1_700_000_000_000L   // fixed reference timestamp

    @BeforeEach
    fun setUp() {
        gobCalculator = GobCalculator(persistenceLayer, dateUtil)
        whenever(dateUtil.now()).thenReturn(now)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Build a valid GLUCAGON BS record at [minutesAgo] before [now]. */
    private fun glucagonDose(doseMcg: Double, minutesAgo: Long): BS =
        BS(
            timestamp = now - minutesAgo * 60_000L,
            amount = doseMcg / 1000.0,   // mcg → mg (storage unit)
            type = BS.Type.GLUCAGON,
            isValid = true
        )

    /** Expected GOB contribution of a single dose with a given half-life. */
    private fun expectedContribution(doseMcg: Double, minutesAgo: Double, halfLife: Int): Double =
        doseMcg * exp(-minutesAgo * ln(2.0) / halfLife)

    private fun stubDoses(vararg doses: BS) {
        whenever(
            persistenceLayer.getBolusesFromTimeToTime(
                org.mockito.kotlin.any(),
                org.mockito.kotlin.any(),
                org.mockito.kotlin.any()
            )
        ).thenReturn(doses.toList())
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    fun `returns zero GOB when no doses in lookback window`() {
        stubDoses()
        assertThat(gobCalculator.currentGob(20)).isWithin(0.001).of(0.0)
    }

    @Test
    fun `dose delivered 20 minutes ago decays to exactly half at half-life boundary`() {
        val doseMcg = 100.0
        val halfLife = 20
        stubDoses(glucagonDose(doseMcg, minutesAgo = 20))

        val gob = gobCalculator.currentGob(halfLife)

        // After exactly one half-life, GOB should be 50 mcg
        assertThat(gob).isWithin(0.5).of(50.0)
    }

    @Test
    fun `immediate dose contributes full dose to GOB`() {
        // 1 minute ago ≈ immediate (small decay)
        val doseMcg = 100.0
        val halfLife = 20
        stubDoses(glucagonDose(doseMcg, minutesAgo = 1))

        val gob = gobCalculator.currentGob(halfLife)
        val expected = expectedContribution(doseMcg, 1.0, halfLife)

        assertThat(gob).isWithin(0.5).of(expected)
    }

    @Test
    fun `dose delivered 60 minutes ago with 20 min half-life decays significantly`() {
        val doseMcg = 100.0
        val halfLife = 20
        stubDoses(glucagonDose(doseMcg, minutesAgo = 60))

        val gob = gobCalculator.currentGob(halfLife)
        // 3 half-lives → 100 × 0.125 = 12.5 mcg
        assertThat(gob).isWithin(1.0).of(12.5)
    }

    @Test
    fun `multiple doses sum correctly`() {
        val halfLife = 20
        val dose1Mcg = 100.0
        val dose2Mcg = 75.0
        val minutes1Ago = 10L
        val minutes2Ago = 30L

        stubDoses(
            glucagonDose(dose1Mcg, minutesAgo = minutes1Ago),
            glucagonDose(dose2Mcg, minutesAgo = minutes2Ago)
        )

        val gob = gobCalculator.currentGob(halfLife)
        val expected = expectedContribution(dose1Mcg, minutes1Ago.toDouble(), halfLife) +
            expectedContribution(dose2Mcg, minutes2Ago.toDouble(), halfLife)

        assertThat(gob).isWithin(0.5).of(expected)
    }

    @Test
    fun `only GLUCAGON type records are included in GOB`() {
        val halfLife = 20
        val glucagonDose = glucagonDose(100.0, minutesAgo = 10)
        val insulinBolus = BS(
            timestamp = now - 10 * 60_000L,
            amount = 2.0,
            type = BS.Type.NORMAL,
            isValid = true
        )

        stubDoses(glucagonDose, insulinBolus)

        val gob = gobCalculator.currentGob(halfLife)
        // Only the glucagon dose should be counted
        val expected = expectedContribution(100.0, 10.0, halfLife)
        assertThat(gob).isWithin(0.5).of(expected)
    }

    @Test
    fun `invalid doses are excluded from GOB`() {
        val halfLife = 20
        val validDose = glucagonDose(100.0, minutesAgo = 10)
        val invalidDose = BS(
            timestamp = now - 5 * 60_000L,
            amount = 200.0 / 1000.0,
            type = BS.Type.GLUCAGON,
            isValid = false
        )

        stubDoses(validDose, invalidDose)

        val gob = gobCalculator.currentGob(halfLife)
        val expected = expectedContribution(100.0, 10.0, halfLife)
        assertThat(gob).isWithin(0.5).of(expected)
    }

    @Test
    fun `longer half-life results in higher GOB for same dose`() {
        val doseMcg = 100.0
        val minutesAgo = 30L
        stubDoses(glucagonDose(doseMcg, minutesAgo))

        val gobShortHalfLife = gobCalculator.currentGob(20)
        val gobLongHalfLife = gobCalculator.currentGob(60)

        assertThat(gobLongHalfLife).isGreaterThan(gobShortHalfLife)
    }

    @Test
    fun `GOB is always non-negative`() {
        val doseMcg = 50.0
        val minutesAgo = 180L   // 3 hours — near edge of lookback window
        stubDoses(glucagonDose(doseMcg, minutesAgo))

        val gob = gobCalculator.currentGob(20)
        assertThat(gob).isAtLeast(0.0)
    }
}
