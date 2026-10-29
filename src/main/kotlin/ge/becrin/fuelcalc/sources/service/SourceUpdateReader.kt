package ge.becrin.fuelcalc.sources.service

import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.sources.PriceSource
import ge.becrin.fuelcalc.sources.SourceUpdateStatus
import ge.becrin.fuelcalc.sources.dto.SourceUpdateDto
import ge.becrin.fuelcalc.sources.jpa.ImportStateRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class SourceUpdateReader(private val states: ImportStateRepository) {
    @Transactional(readOnly = true)
    fun read(frequencies: Collection<PriceFrequency> = PriceFrequency.entries): Map<String, SourceUpdateDto> {
        val saved = states.findAll().associateBy { it.code }

        return PriceSource.entries.filter { it.frequency in frequencies }.associate { source ->
            val state = saved[source.name]
            val status =
                when {
                    state?.errorMessage != null -> SourceUpdateStatus.UNAVAILABLE
                    state?.lastSuccessAt != null -> SourceUpdateStatus.READY
                    else -> SourceUpdateStatus.UNKNOWN
                }

            source.name to SourceUpdateDto(source, source.frequency, status, state?.lastSuccessAt)
        }
    }
}
