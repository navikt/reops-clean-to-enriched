package no.nav.reops.opensearch

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import org.opensearch.protobufs.services.DocumentServiceGrpc
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
class OpenSearchClientConfiguration(
    @Value("\${reops.opensearch.host}") private val host: String,
    @Value("\${reops.opensearch.port:9400}") private val port: Int,
) {
    @Bean(destroyMethod = "shutdown")
    fun opensearchChannel(): ManagedChannel {
        return ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .keepAliveTime(20, TimeUnit.SECONDS)
            .keepAliveTimeout(5, TimeUnit.SECONDS)
            .idleTimeout(60, TimeUnit.SECONDS)
            .maxInboundMessageSize(10 * 1024 * 1024)
            .build()
    }

    @Bean
    fun documentService(opensearchChannel: ManagedChannel): DocumentServiceGrpc.DocumentServiceBlockingStub {
        return DocumentServiceGrpc.newBlockingStub(opensearchChannel)
    }
}

