package ge.becrin.fuelcalc.sources.service

import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.sources.PriceSource
import ge.becrin.fuelcalc.sources.SourceUpdateStatus
import ge.becrin.fuelcalc.sources.dto.SourceUpdateDto
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class SourceUpdateReader(private val jdbc: JdbcTemplate) {
    data class Updates(
        val byFeed: Map<Long, SourceUpdateDto>,

        val bySource: Map<Pair<PriceSource, PriceFrequency>, SourceUpdateDto>
    ) {
        fun feed(id: Long): SourceUpdateDto =
            byFeed[id] ?: unknown()

        fun source(
            provider: PriceSource,
            frequency: PriceFrequency
        ): SourceUpdateDto =
            bySource[provider to frequency] ?: unknown()

        private fun unknown(): SourceUpdateDto =
            SourceUpdateDto(SourceUpdateStatus.UNKNOWN, null, null, null)
    }

    fun read(): Updates {
        val byFeed = mutableMapOf<Long, SourceUpdateDto>()
        val bySource = mutableMapOf<Pair<PriceSource, PriceFrequency>, SourceUpdateDto>()
        jdbc.query("SELECT * FROM source_feeds ORDER BY id") { row ->
            val status = SourceUpdateStatus.valueOf(row.getString("update_status"))
            val message =
                when (status) {
                    SourceUpdateStatus.UNAVAILABLE -> "Источник временно недоступен; показаны сохранённые данные"
                    SourceUpdateStatus.DELAYED -> "Обновление источника задерживается"
                    else -> null
                }
            val value = SourceUpdateDto(
                status,
                row.getTimestamp("last_success_at")?.toInstant(),
                row.getTimestamp("next_expected_publication_at")?.toInstant(),
                message
            )
            byFeed[row.getLong("id")] = value
            bySource[PriceSource.valueOf(row.getString("provider_code")) to
                PriceFrequency.valueOf(row.getString("frequency"))] = value
        }

        return Updates(byFeed, bySource)
    }
}
