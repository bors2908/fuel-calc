package ge.becrin.fuelcalc.sources.benzup.backfill

import ge.becrin.fuelcalc.sources.service.SourceImportCompleted
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "imports.benzup", name = ["test-backfill-enabled"], havingValue = "true")
class BenzupTestBackfill(private val store: BenzupTestBackfillStore) {
    @EventListener
    fun afterImport(event: SourceImportCompleted) {
        log.warn(
            "\n!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n" +
                "!!! TEST-ONLY BENZUP BACKFILL: DO NOT USE IN PRODUCTION !!!\n" +
                "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n" +
                "[triggerSource={}, property=imports.benzup.test-backfill-enabled]",
            event.source
        )

        try {
            val result = store.fill()

            log.info(
                "Test BenzUp backfill completed [inserted={}, skippedSeries={}, missingOfficialDays={}]",
                result.inserted, result.skippedSeries, result.missingOfficialDays
            )
        } catch (error: Exception) {
            log.error("Test BenzUp backfill failed; successful source import retained", error)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(BenzupTestBackfill::class.java)
    }
}
