package no.nav.reops.bigquery

import com.google.api.gax.core.NoCredentialsProvider
import com.google.api.gax.grpc.GrpcTransportChannel
import com.google.api.gax.rpc.FixedTransportChannelProvider
import com.google.cloud.bigquery.storage.v1.BigQueryWriteClient
import com.google.cloud.bigquery.storage.v1.BigQueryWriteSettings
import io.grpc.ManagedChannelBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
class BigQueryConfiguration {

    @Bean
    @Profile("!local")
    fun bigQueryWriteClient(): BigQueryWriteClient {
        return BigQueryWriteClient.create()
    }

    @Bean
    @Profile("local")
    fun localBigQueryWriteClient(
        @Value("\${reops.bigquery.endpoint}") endpoint: String
    ): BigQueryWriteClient {
        val channel = ManagedChannelBuilder.forTarget(endpoint).usePlaintext().build()
        val settings = BigQueryWriteSettings.newBuilder()
            .setTransportChannelProvider(FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel)))
            .setCredentialsProvider(NoCredentialsProvider.create())
            .build()
        return BigQueryWriteClient.create(settings)
    }
}
