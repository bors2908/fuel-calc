package ge.becrin.fuelcalc.archive.service

import ge.becrin.fuelcalc.api.dto.ApiMetaDto
import ge.becrin.fuelcalc.api.dto.ErrorDetailDto
import ge.becrin.fuelcalc.api.error.ApiException
import ge.becrin.fuelcalc.api.web.ApiRequestContext
import ge.becrin.fuelcalc.archive.jpa.ArchiveStateRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class ArchiveReadService(
    private val archive: ArchiveStateRepository,

    private val context: ApiRequestContext
) {
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun <T> read(
        expectedDataVersion: String? = null,
        action: (ApiMetaDto) -> T
    ): T {
        val revision = archive.readRevision()
        val info = context.current()
        val version = revision.toString()

        if (expectedDataVersion != null && expectedDataVersion != version) {
            throw ApiException(
                409,
                "DataVersionChanged",
                "Данные изменились",
                listOf(
                    ErrorDetailDto("dataVersion", "DATA_VERSION_CHANGED", "Повторите загрузку с актуальной ревизией")
                ),
                version
            )
        }

        return action(ApiMetaDto(info.requestId, info.generatedAt, info.serverDate, version))
    }
}
