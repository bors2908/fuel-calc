package ge.becrin.fuelcalc.sources.service

import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.sources.PriceSource
import ge.becrin.fuelcalc.sources.dto.SourceUpdateDto
import ge.becrin.fuelcalc.sources.jpa.SourceFeedRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class SourceUpdateReader(private val feeds: SourceFeedRepository) {
    @Transactional(readOnly = true)
    fun read(frequencies: Collection<PriceFrequency> = PriceFrequency.entries): Map<String, SourceUpdateDto> {
        if (frequencies.isEmpty()) {
            return emptyMap()
        }

        return feeds.findForFrequencies(frequencies).associate { feed ->
            feed.code to SourceUpdateDto(
                provider = PriceSource.valueOf(feed.provider.code),
                frequency = feed.frequency,
                status = feed.updateStatus,
                lastSuccessAt = feed.lastSuccessAt,
                nextExpectedPublicationAt = feed.nextExpectedPublicationAt
            )
        }
    }
}
