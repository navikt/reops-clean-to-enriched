plugins {
	kotlin("jvm")
	id("io.spring.dependency-management")
	id("org.springframework.boot") apply false
}

dependencyManagement {
	imports {
		mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
	}
}

dependencies {
	api("tools.jackson.module:jackson-module-kotlin")
	api("org.jetbrains.kotlin:kotlin-stdlib")
}
