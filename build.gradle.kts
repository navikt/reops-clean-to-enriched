import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
	id("org.springframework.boot") version "4.0.5"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.graalvm.buildtools.native") version "1.0.0"
	kotlin("jvm") version "2.3.20"
	kotlin("plugin.spring") version "2.3.20"
	kotlin("plugin.jpa") version "2.3.20"
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

configurations.configureEach {
	exclude(group = "org.lz4", module = "lz4-java")
	resolutionStrategy.capabilitiesResolution.withCapability("org.lz4", "lz4-java") {
		select(candidates.first { it.id.let { id -> id is ModuleComponentIdentifier && id.group == "at.yawk.lz4" } })
	}
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("org.springframework.boot:spring-boot-starter-kafka")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")

	implementation("org.postgresql:postgresql")
	implementation("org.flywaydb:flyway-database-postgresql")

	implementation("org.springframework.boot:spring-boot-starter-cache")
	implementation("com.github.ben-manes.caffeine:caffeine")
	implementation("tools.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib")
	implementation("at.yawk.lz4:lz4-java:1.11.0")
	implementation("io.micrometer:micrometer-registry-prometheus")
	implementation("com.maxmind.geoip2:geoip2:5.0.2")

	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
	testImplementation("org.springframework.kafka:spring-kafka-test")
	testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")
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
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<ProcessResources>("processResources") {
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

graalvmNative {
	binaries {
		named("main") {
			imageName.set("app")
			mainClass.set("no.nav.reops.UmamiApplicationKt")
		}
	}
	binaries.all {
		buildArgs.addAll(
			"-H:+ReportExceptionStackTraces",
			"--exclude-config", ".*/java\\.desktop/.*",
			"-H:-IncludeMethodData",
			"-Os",
		)
		if (System.getenv("CI").toBoolean()) {
			buildArgs.add("-J-Xmx32g")
		} else {
			buildArgs.add("-J-Xmx6g")
		}
	}
}