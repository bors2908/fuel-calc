package ge.becrin.fuelcalc.sources.jpa

import ge.becrin.fuelcalc.archive.jpa.ArchiveStateRepository
import ge.becrin.fuelcalc.sources.PriceSource
import java.time.Clock
import java.time.LocalDate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class ImportStateStore(
    private val archive: ArchiveStateRepository,

    private val repository: ImportStateRepository,

    private val clock: Clock
) {
    @Transactional(readOnly = true)
    fun fetchedThrough(source: PriceSource = PriceSource.OFFICIAL): LocalDate? =
        repository.findById(source.name).orElse(null)?.fetchedThrough

    @Transactional(propagation = Propagation.MANDATORY)
    fun source(source: PriceSource): ImportStateEntity =
        state(source.name)

    @Transactional(propagation = Propagation.MANDATORY)
    fun regions(): ImportStateEntity =
        state("REGIONS")

    @Transactional
    fun failed(
        source: PriceSource,
        error: Exception
    ) {
        archive.lockForPublication()

        val state = state(source.name)
        state.lastAttemptAt = clock.instant()
        state.errorMessage = error.message?.take(2000) ?: error.javaClass.simpleName
    }

    private fun state(code: String): ImportStateEntity =
        repository.findById(code).orElseGet { repository.save(ImportStateEntity(code = code)) }
}
