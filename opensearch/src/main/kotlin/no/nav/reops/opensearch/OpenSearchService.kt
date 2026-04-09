package no.nav.reops.opensearch

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.protobuf.ByteString
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import no.nav.reops.event.EnrichedEvent
import org.opensearch.protobufs.BulkRequest
import org.opensearch.protobufs.BulkRequestBody
import org.opensearch.protobufs.IndexOperation
import org.opensearch.protobufs.OperationContainer
import org.opensearch.protobufs.services.DocumentServiceGrpc
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@Service
class OpenSearchService(
    private val documentService: DocumentServiceGrpc.DocumentServiceBlockingStub,
    @Value("\${reops.opensearch.index:events}") private val indexName: String,
    private val objectMapper: ObjectMapper,
    meterRegistry: MeterRegistry
) {
    private val opensearchRequestsSuccess: Counter =
        meterRegistry.counter("opensearch_requests_total", "result", "success")
    private val opensearchRequestsFailure: Counter =
        meterRegistry.counter("opensearch_requests_total", "result", "failure")

    fun sendEvents(events: List<EnrichedEvent>) {
        if (events.isEmpty()) return

        val dailyIndex = "$indexName-${LocalDate.now().format(DATE_SUFFIX)}"
        val indexOp = OperationContainer.newBuilder()
            .setIndex(IndexOperation.newBuilder().build())

        val bulkRequestBuilder = BulkRequest.newBuilder().setIndex(dailyIndex)
        for (event in events) {
            val document = OpenSearchDocument(event = event)
            val documentBytes = objectMapper.writeValueAsBytes(document)
            bulkRequestBuilder.addBulkRequestBody(
                BulkRequestBody.newBuilder()
                    .setOperationContainer(indexOp)
                    .setObject(ByteString.copyFrom(documentBytes))
            )
        }
        val bulkRequest = bulkRequestBuilder.build()

        var lastException: Exception? = null
        for (attempt in 0..MAX_RETRIES) {
            try {
                val response = documentService
                    .withDeadlineAfter(10, TimeUnit.SECONDS)
                    .bulk(bulkRequest)

                if (response.errors) {
                    throw RuntimeException("OpenSearch bulk indexing failed")
                }

                opensearchRequestsSuccess.increment(events.size.toDouble())
                return
            } catch (ex: StatusRuntimeException) {
                lastException = ex
                if (attempt < MAX_RETRIES && isRetryable(ex.status)) {
                    val delay = RETRY_DELAY_MS * (1L shl attempt)
                    LOG.warn("Retrying after gRPC error (attempt {}): {}", attempt + 1, ex.status)
                    Thread.sleep(delay)
                    continue
                }
                break
            } catch (ex: Exception) {
                lastException = ex
                break
            }
        }

        opensearchRequestsFailure.increment(events.size.toDouble())
        LOG.error("Failed to send {} events to OpenSearch", events.size, lastException)
        throw lastException!!
    }

    private fun isRetryable(status: Status): Boolean =
        status.code == Status.Code.UNAVAILABLE || status.code == Status.Code.DEADLINE_EXCEEDED

    private companion object {
        private val LOG = LoggerFactory.getLogger(OpenSearchService::class.java)
        private val DATE_SUFFIX: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 500L
    }
}

data class OpenSearchDocument(
    @field:JsonProperty("@timestamp") val timestamp: Instant = Instant.now(),
    val event: EnrichedEvent
)

