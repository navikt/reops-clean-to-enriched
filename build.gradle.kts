import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
	kotlin("plugin.spring") version "2.3.20" apply false
	kotlin("plugin.jpa") version "2.3.20" apply false
	id("org.springframework.boot") version "4.0.5" apply false
	id("io.spring.dependency-management") version "1.1.7" apply false
	id("org.graalvm.buildtools.native") version "1.0.0" apply false
	kotlin("jvm") version "2.3.20"
}

subprojects {
	group = "no.nav.reops"
	version = "0.0.1-SNAPSHOT"

	repositories {
		mavenCentral()
	}

	configurations.configureEach {
		exclude(group = "org.lz4", module = "lz4-java")
		resolutionStrategy.capabilitiesResolution.withCapability("org.lz4", "lz4-java") {
			select(candidates.first { it.id.let { id -> id is ModuleComponentIdentifier && id.group == "at.yawk.lz4" } })
		}
	}

	pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
		extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
			compilerOptions {
				freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
			}
		}

		extensions.configure<JavaPluginExtension> {
			toolchain {
				languageVersion = JavaLanguageVersion.of(
					providers.gradleProperty("javaVersion").getOrElse("25")
				)
			}
		}
	}

	tasks.withType<Test> {
		useJUnitPlatform()
		jvmArgs("--enable-native-access=ALL-UNNAMED")
	}

	// Workaround for graalvm/native-build-tools#760:
	// collectReachabilityMetadata is broken on Gradle 9 in multi-module projects
	pluginManager.withPlugin("org.graalvm.buildtools.native") {
		tasks.matching { it.name == "collectReachabilityMetadata" }.configureEach {
			enabled = false
		}
	}
}
dependencies {
	implementation(kotlin("stdlib-jdk8"))
}
repositories {
	mavenCentral()
}