package ge.becrin.fuelcalc.sources.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface SourceFetchStateRepository : JpaRepository<SourceFetchStateEntity, Long> {
    @Query("SELECT state FROM SourceFetchState state WHERE state.feed.code = :code")
    fun findByFeedCode(@Param("code") code: String): SourceFetchStateEntity?

    @Query(
        """
        SELECT state
        FROM SourceFetchState state
            JOIN FETCH state.feed feed
        WHERE feed.code = :code
            AND feed.enabled = true
            AND (state.nextFetchNotBefore IS NULL OR state.nextFetchNotBefore <= :now)
        """
    )
    fun findReady(
        @Param("code") code: String,
        @Param("now") now: Instant
    ): SourceFetchStateEntity?

    @Query("SELECT state FROM SourceFetchState state WHERE state.feed.provider.code = :provider")
    fun findForProvider(@Param("provider") provider: String): List<SourceFetchStateEntity>
}
