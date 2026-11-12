import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.GZIPInputStream

plugins {
	id("org.springframework.boot") version "4.1.2"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("jvm") version "2.4.21"
	kotlin("plugin.spring") version "2.4.21"
	kotlin("plugin.jpa") version "2.4.21"
}

group = "no.nav.reops"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(
			providers.gradleProperty("javaVersion").getOrElse("25")
		)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	constraints {
		implementation("at.yawk.lz4:lz4-java:1.14.0") {
			because("CVEs in transitive 1.10.1 from kafka-clients")
		}
		implementation("org.xerial.snappy:snappy-java:1.1.10.12") {
			because("CVEs in transitive 1.1.10.7 from kafka-clients")
		}
	}

	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("org.springframework.boot:spring-boot-starter-webclient")
	implementation("org.springframework.boot:spring-boot-starter-kafka")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")

	implementation("org.postgresql:postgresql")
	implementation("org.flywaydb:flyway-database-postgresql")

	implementation("org.springframework.boot:spring-boot-starter-cache")
	implementation("com.github.ben-manes.caffeine:caffeine")
	implementation("tools.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib")
	implementation("io.micrometer:micrometer-registry-prometheus")

	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
	testImplementation("org.springframework.kafka:spring-kafka-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.mockito.kotlin:mockito-kotlin:6.4.0")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("com.h2database:h2")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
	jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
	jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.named<Jar>("bootJar") {
	archiveFileName.set("app.jar")
	exclude("**/dbip-country-lite.mmdb")
}

// Fetch the provider's current snapshot on EVERY resource build, including nightly
// rebuilds of the same production tag. Never use Gradle's up-to-date/build cache.
val fetchIpCountry by tasks.registering {
	val generatedResources = layout.buildDirectory.dir("generated/ip-country")
	outputs.dir(generatedResources)
	outputs.upToDateWhen { false }
	outputs.cacheIf { false }

	doLast {
		val output = generatedResources.get().dir("geoip").asFile.apply { mkdirs() }
		val metadata = StringBuilder("provider=IPtoASN\nlicense=PDDL-1.0\n")
		for (name in listOf("ip2country-v4-u32.tsv.gz", "ip2country-v6.tsv.gz")) {
			val source = "https://iptoasn.com/data/$name"
			val connection = URI(source).toURL().openConnection() as HttpURLConnection
			connection.connectTimeout = 20_000
			connection.readTimeout = 60_000
			connection.useCaches = false
			connection.setRequestProperty("Cache-Control", "no-cache, no-store")
			connection.setRequestProperty("User-Agent", "reops-clean-to-enriched/geoip-build")
			val downloaded = temporaryDir.resolve(name)
			try {
				check(connection.responseCode == 200) { "IPtoASN download failed: $source (HTTP ${connection.responseCode})" }
				connection.inputStream.use { input ->
					val bytes = input.readNBytes(16_777_217)
					check(bytes.size in 1..16_777_216) { "Unexpected IPtoASN download size: $name (${bytes.size} bytes)" }
					downloaded.writeBytes(bytes)
				}
				var rows = 0
				GZIPInputStream(downloaded.inputStream()).bufferedReader(Charsets.UTF_8).useLines { lines ->
					lines.forEach { line ->
						val fields = line.split('\t')
						check(fields.size == 3 && fields[0].isNotBlank() && fields[1].isNotBlank() &&
							(fields[2] in listOf("None", "Unknown") || fields[2].matches(Regex("[A-Z]{2}")))) {
							"Invalid IPtoASN country row in $name at line ${rows + 1}"
						}
						rows++
					}
				}
				check(rows > 0) { "Empty IPtoASN country dataset: $name" }
				val sha256 = MessageDigest.getInstance("SHA-256").digest(downloaded.readBytes())
					.joinToString("") { "%02x".format(it) }
				metadata.append("$name.source=$source\n")
				metadata.append("$name.fetched-at=${Instant.now()}\n")
				metadata.append("$name.last-modified=${connection.getHeaderField("Last-Modified").orEmpty()}\n")
				metadata.append("$name.sha256=$sha256\n$name.rows=$rows\n")
				downloaded.copyTo(output.resolve(name), overwrite = true)
				logger.lifecycle("Fetched IPtoASN {}: {} ranges, SHA-256 {}", name, rows, sha256)
			} finally {
				connection.disconnect()
			}
		}
		output.resolve("metadata.properties").writeText(metadata.toString())
	}
}

sourceSets.main {
	resources.srcDir(fetchIpCountry)
}
