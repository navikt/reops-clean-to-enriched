plugins {
	id("org.springframework.boot")
	id("io.spring.dependency-management")
	id("org.graalvm.buildtools.native")
	kotlin("jvm")
	kotlin("plugin.spring")
}

dependencies {
	implementation(project(":contract"))

	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-kafka")
	implementation("org.springframework.boot:spring-boot-starter-validation")

	implementation("tools.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("at.yawk.lz4:lz4-java:1.11.0")
	implementation("io.micrometer:micrometer-registry-prometheus")

	implementation(platform("com.google.cloud:libraries-bom:26.79.0"))
	implementation("com.google.cloud:google-cloud-bigquerystorage")

	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.kafka:spring-kafka-test")
	testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
	jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.named<Jar>("bootJar") {
	archiveFileName.set("app.jar")
}

graalvmNative {
	binaries {
		named("main") {
			imageName.set("app")
			mainClass.set("no.nav.reops.BigqueryApplicationKt")
		}
	}
	binaries.all {
		buildArgs.addAll(
			"-H:+ReportExceptionStackTraces",
			"--exclude-config", ".*/java\\.desktop/.*",
			"-H:-IncludeMethodData",
			"-O2",
		)
		if (System.getenv("CI").toBoolean()) {
			buildArgs.add("-J-Xmx32g")
		} else {
			buildArgs.add("-J-Xmx6g")
		}
	}
}
