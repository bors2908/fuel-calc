package ge.becrin.fuelcalc.sources.service

import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.sources.model.FeedDefinition
import ge.becrin.fuelcalc.sources.model.FetchWindow
import ge.becrin.fuelcalc.sources.model.PreparedPublication

interface RegionalSourceAdapter {
    val provider: String

    val enabled: Boolean

    fun beginRun() {
    }

    fun definition(frequency: PriceFrequency): FeedDefinition

    fun fetch(
        feed: FeedDefinition,
        window: FetchWindow
    ): List<PreparedPublication>
}
