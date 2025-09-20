plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.spring") version "2.0.0"
    id("org.springframework.boot") version "3.3.2"
//    id("io.spring.dependency-management") version "1.1.7"
    id("com.github.ben-manes.versions") version "0.51.0" //introduced for proper auto-updates, like with android studio.

    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories { mavenCentral() }

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.3.2")) //required for jdbc/db connection
    implementation("org.springframework.boot:spring-boot-starter-actuator")


    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.test { useJUnitPlatform() }

application { mainClass.set("fhir.ApplicationKt") }
springBoot { mainClass.set("fhir.ApplicationKt") }
