package ge.becrin.fuelcalc.prices.service

import ge.becrin.fuelcalc.prices.MissingPriceReason
import ge.becrin.fuelcalc.prices.dto.AvailablePriceCellDto
import ge.becrin.fuelcalc.prices.dto.MissingPriceCellDto
import ge.becrin.fuelcalc.prices.dto.PriceFreshnessDto
import ge.becrin.fuelcalc.prices.dto.PricePeriodDto
import ge.becrin.fuelcalc.prices.model.PriceRow
import ge.becrin.fuelcalc.sources.service.SourceUpdateReader
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class PriceCellConverter {
    fun available(
        row: PriceRow,
        latest: LocalDate?,
        updates: SourceUpdateReader.Updates
    ): AvailablePriceCellDto =
        AvailablePriceCellDto(
            row.price.toPlainString(),
            row.id.toString(),
            row.period,
            row.source,
            PriceFreshnessDto(latest, latest?.let { row.period.referenceDate == it }, updates.feed(row.feedId)),
            null
        )

    fun missing(
        reason: MissingPriceReason = MissingPriceReason.NO_DATA_IN_RANGE,
        period: PricePeriodDto? = null
    ): MissingPriceCellDto =
        MissingPriceCellDto(null, null, period, null, null, reason.name)
}
