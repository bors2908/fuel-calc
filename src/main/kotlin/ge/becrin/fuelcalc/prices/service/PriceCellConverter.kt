package ge.becrin.fuelcalc.prices.service

import ge.becrin.fuelcalc.prices.MissingPriceReason
import ge.becrin.fuelcalc.prices.dto.AvailablePriceCellDto
import ge.becrin.fuelcalc.prices.dto.MissingPriceCellDto
import ge.becrin.fuelcalc.prices.dto.PricePeriodDto
import ge.becrin.fuelcalc.prices.model.PriceRow
import org.springframework.stereotype.Component

@Component
class PriceCellConverter {
    fun available(row: PriceRow): AvailablePriceCellDto =
        AvailablePriceCellDto(row.price.toPlainString(), row.id?.toString(), PricePeriodDto(row.period), row.source, row.forecast)

    fun missing(): MissingPriceCellDto =
        MissingPriceCellDto(MissingPriceReason.NO_DATA_IN_RANGE)
}
