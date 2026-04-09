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
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("org.springframework.boot:spring-boot-starter-kafka")
	implementation("org.springframework.boot:spring-boot-starter-validation")

	implementation("tools.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("at.yawk.lz4:lz4-java:1.10.4")
	implementation("io.micrometer:micrometer-registry-prometheus")

	implementation(platform("io.grpc:grpc-bom:1.72.0"))
	implementation("io.grpc:grpc-netty-shaded")
	implementation("io.grpc:grpc-protobuf")
	implementation("io.grpc:grpc-stub")
	implementation("org.opensearch:protobufs:1.3.0")

	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
	testImplementation("org.springframework.kafka:spring-kafka-test")
	testImplementation("org.mockito.kotlin:mockito-kotlin:6.2.3")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.wiremock:wiremock-grpc-extension:1.0.0-beta.5")
	testImplementation("org.wiremock.integrations:wiremock-spring-boot:4.2.1")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
	jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.named<Jar>("bootJar") {
	archiveFileName.set("app.jar")
}

val generateGrpcDescriptors by tasks.registering(JavaExec::class) {
	group = "build"
	description = "Generate WireMock gRPC descriptor files from compiled proto classes"
	classpath = sourceSets["test"].runtimeClasspath
	mainClass.set("no.nav.reops.GenerateDescriptorsKt")
	args = listOf("${projectDir}/src/test/resources/wiremock/grpc/opensearch.dsc")
	dependsOn("testClasses")
}

graalvmNative {
	binaries {
		named("main") {
			imageName.set("app")
			mainClass.set("no.nav.reops.OpenSearchApplicationKt")
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
