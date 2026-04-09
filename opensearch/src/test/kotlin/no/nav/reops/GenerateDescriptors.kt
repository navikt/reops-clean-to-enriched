package no.nav.reops

import com.google.protobuf.DescriptorProtos.FileDescriptorSet
import com.google.protobuf.Descriptors.FileDescriptor
import org.opensearch.protobufs.services.DocumentServiceProto
import java.io.File

fun main(args: Array<String>) {
    val outputPath = args.firstOrNull()
        ?: "src/test/resources/wiremock/grpc/opensearch.dsc"

    val visited = mutableSetOf<String>()
    val ordered = mutableListOf<FileDescriptor>()

    fun collect(fd: FileDescriptor) {
        if (!visited.add(fd.fullName)) return
        fd.dependencies.forEach { collect(it) }
        ordered.add(fd)
    }

    collect(DocumentServiceProto.getDescriptor())

    val descriptorSet = FileDescriptorSet.newBuilder()
        .addAllFile(ordered.map { it.toProto() })
        .build()

    File(outputPath).also { it.parentFile.mkdirs() }.writeBytes(descriptorSet.toByteArray())
    println("Generated descriptor set with ${ordered.size} file(s) at $outputPath")
}
