package ge.becrin.fuelcalc.prices.jpa

import ge.becrin.fuelcalc.sources.jpa.SourcePublicationEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RegionalPriceObservationRepository : JpaRepository<RegionalPriceObservationEntity, Long> {
    @Query(
        """
        SELECT observation
        FROM RegionalPriceObservation observation
        WHERE observation.publication IN :publications
            AND observation.priceKey IN :keys
        """
    )
    fun findPublished(
        @Param("publications") publications: Collection<SourcePublicationEntity>,
        @Param("keys") keys: Collection<RegionalPriceKeyEntity>
    ): List<RegionalPriceObservationEntity>
}
