package no.nav.reops

import no.nav.reops.event.EnrichedEvent
import no.nav.reops.event.EventDataEntry
import no.nav.reops.event.Session
import no.nav.reops.event.SessionDataEntry
import no.nav.reops.event.Website
import no.nav.reops.event.WebsiteChangeEvent
import no.nav.reops.event.WebsiteEventStatus
import no.nav.reops.event.WebsiteEvent
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.RuntimeHints
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ImportRuntimeHints

@Configuration
@ImportRuntimeHints(NativeHintsRegistrar::class)
class NativeHints

class NativeHintsRegistrar : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        val reflection = hints.reflection()

        // Register all data classes used for Jackson serialization/deserialization
        listOf(
            EnrichedEvent::class.java,
            WebsiteEvent::class.java,
            Session::class.java,
            EventDataEntry::class.java,
            SessionDataEntry::class.java,
            Website::class.java,
            WebsiteChangeEvent::class.java,
            WebsiteEventStatus::class.java,
            ErrorResponse::class.java,
        ).forEach { clazz ->
            reflection.registerType(
                clazz,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.ACCESS_DECLARED_FIELDS,
                MemberCategory.ACCESS_PUBLIC_FIELDS,
            )
        }

        // jackson-module-kotlin 3.x reflectively accesses the static INSTANCE field
        // on Kotlin object / companion object classes. Register all Kotlin-generated
        // companion classes whose enclosing data classes are serialised by Jackson.
        listOf(
            "no.nav.reops.event.EnrichedEvent\$Companion",
            "no.nav.reops.event.WebsiteEvent\$Companion",
            "no.nav.reops.event.Session\$Companion",
            "no.nav.reops.event.EventDataEntry\$Companion",
            "no.nav.reops.event.SessionDataEntry\$Companion",
            "no.nav.reops.event.Website\$Companion",
            "no.nav.reops.event.WebsiteChangeEvent\$Companion",
        ).forEach { className ->
            val resolvedClass = classLoader?.let { runCatching { Class.forName(className, false, it) }.getOrNull() }
            if (resolvedClass != null) {
                reflection.registerType(
                    resolvedClass,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.ACCESS_DECLARED_FIELDS,
                    MemberCategory.ACCESS_PUBLIC_FIELDS,
                )
                runCatching { resolvedClass.getDeclaredField("INSTANCE") }.getOrNull()?.let { field ->
                    reflection.registerField(field)
                }
            }
        }

        // Register LZ4 / XXHash classes used by Kafka (loaded via reflection).
        // Kafka accesses the static INSTANCE field reflectively, so we register the field explicitly.
        listOf(
            "net.jpountz.lz4.LZ4Factory",
            "net.jpountz.xxhash.XXHashFactory",
            "net.jpountz.xxhash.StreamingXXHash32\$Factory",
            "net.jpountz.xxhash.StreamingXXHash64\$Factory",
            "net.jpountz.xxhash.StreamingXXHash32JavaSafe\$Factory",
            "net.jpountz.xxhash.StreamingXXHash64JavaSafe\$Factory",
            "net.jpountz.xxhash.StreamingXXHash32JavaUnsafe\$Factory",
            "net.jpountz.xxhash.StreamingXXHash64JavaUnsafe\$Factory",
            "net.jpountz.lz4.LZ4JavaSafeCompressor",
            "net.jpountz.lz4.LZ4HCJavaSafeCompressor",
            "net.jpountz.lz4.LZ4JavaSafeFastDecompressor",
            "net.jpountz.lz4.LZ4JavaSafeSafeDecompressor",
            "net.jpountz.xxhash.XXHash32JavaSafe",
            "net.jpountz.xxhash.XXHash64JavaSafe",
            "net.jpountz.xxhash.StreamingXXHash32JavaSafe",
            "net.jpountz.xxhash.StreamingXXHash64JavaSafe",
        ).forEach { className ->
            val resolvedClass = classLoader?.let { runCatching { Class.forName(className, false, it) }.getOrNull() }
            if (resolvedClass != null) {
                reflection.registerType(
                    resolvedClass,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                    MemberCategory.ACCESS_DECLARED_FIELDS,
                    MemberCategory.ACCESS_PUBLIC_FIELDS,
                )
                // Explicitly register the static INSTANCE field used by lz4-java's factory lookup
                runCatching { resolvedClass.getDeclaredField("INSTANCE") }.getOrNull()?.let { field ->
                    reflection.registerField(field)
                }
            }
        }
    }
}

