package ge.becrin.fuelcalc.sources.jpa

import ge.becrin.fuelcalc.prices.PriceFrequency
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface SourceFeedRepository : JpaRepository<SourceFeedEntity, Long> {
    @Query(
        """
        SELECT feed
        FROM SourceFeed feed
            JOIN FETCH feed.provider
        WHERE feed.frequency IN :frequencies
        ORDER BY feed.code
        """
    )
    fun findForFrequencies(@Param("frequencies") frequencies: Collection<PriceFrequency>): List<SourceFeedEntity>

    @Query("SELECT feed.lastSuccessAt FROM SourceFeed feed WHERE feed.code = :code")
    fun lastSuccess(@Param("code") code: String): Instant?
}
