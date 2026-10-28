package ge.becrin.fuelcalc.sources.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SourcePublicationRepository : JpaRepository<SourcePublicationEntity, Long> {
    @Query(
        """
        SELECT publication
        FROM SourcePublication publication
        WHERE publication.feed = :feed
            AND publication.externalKey = :externalKey
            AND publication.contentHash IN :hashes
        """
    )
    fun findEditions(
        @Param("feed") feed: SourceFeedEntity,
        @Param("externalKey") externalKey: String,
        @Param("hashes") hashes: Collection<String>
    ): List<SourcePublicationEntity>
}
