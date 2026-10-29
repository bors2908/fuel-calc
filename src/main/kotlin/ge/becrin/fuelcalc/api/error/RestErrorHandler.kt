package ge.becrin.fuelcalc.api.error

import ge.becrin.fuelcalc.api.dto.ErrorDetailDto
import ge.becrin.fuelcalc.api.dto.ErrorResponseDto
import ge.becrin.fuelcalc.api.web.ApiRequestContext
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.QueryTimeoutException
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.validation.BindException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice(basePackages = ["ge.becrin.fuelcalc"])
class RestErrorHandler(private val context: ApiRequestContext) {
    @ExceptionHandler(Exception::class)
    fun process(error: Exception): ResponseEntity<ErrorResponseDto> {
        val info = context.current()
        val failure =
            when (error) {
                is ApiException -> error
                is MethodArgumentNotValidException -> binding(error)
                is BindException -> binding(error)
                is MethodArgumentTypeMismatchException -> ApiException.invalid(error.name, "INVALID_FORMAT", "Неверный формат параметра")
                is HttpMessageNotReadableException -> ApiException.invalid(null, "INVALID_FORMAT", "Неверный формат JSON-запроса")
                is HttpRequestMethodNotSupportedException -> ApiException(405, "InvalidInput", "Метод не поддерживается", emptyList())
                is HttpMediaTypeNotSupportedException -> ApiException(415, "InvalidInput", "Требуется application/json", emptyList())
                is DataAccessResourceFailureException, is CannotCreateTransactionException, is QueryTimeoutException -> {
                    ApiException(
                        503,
                        "ServiceUnavailable",
                        "Архив временно недоступен",
                        listOf(
                            ErrorDetailDto(null, "ARCHIVE_UNAVAILABLE", "Не удалось прочитать сохранённые данные")
                        )
                    )
                }
                else -> ApiException(
                    500,
                    "InternalError",
                    "Не удалось обработать запрос",
                    listOf(
                        ErrorDetailDto(null, "UNEXPECTED_ERROR", "Внутренняя ошибка")
                    )
                )
            }

        if (failure.status >= 500) {
            log.error("API request failed [requestId={}, type={}]", info.requestId, failure.type, error)
        }

        return ResponseEntity.status(failure.status).header("Cache-Control", "no-store").body(
            ErrorResponseDto(
                failure.type,
                failure.message.orEmpty(),
                info.generatedAt,
                info.requestId,
                failure.details
            )
        )
    }

    private fun binding(error: BindException): ApiException =
        ApiException(
            400,
            "InvalidInput",
            "Запрос содержит неверные параметры",
            error.bindingResult.fieldErrors.map {
                ErrorDetailDto(it.field, "INVALID_FORMAT", "Неверное или отсутствующее значение поля")
            }
        )

    companion object {
        private val log = LoggerFactory.getLogger(RestErrorHandler::class.java)
    }
}
