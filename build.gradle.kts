plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.spring") version "2.0.0"
    id("org.springframework.boot") version "3.3.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.github.ben-manes.versions") version "0.51.0" //introduced for proper auto-updates, like with android studio.

    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

repositories { mavenCentral() }

val hapiVersion = "7.2.0"
val testContainerVersion = "1.20.2"

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.3.2")) //required for jdbc/db connection
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // Models for R4B (Patient, Bundle, etc.)
    implementation("ca.uhn.hapi.fhir:hapi-fhir-base:${hapiVersion}")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-structures-r4b:${hapiVersion}")
    //required for testing. 
    implementation("ca.uhn.hapi.fhir:hapi-fhir-validation:${hapiVersion}")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-validation-resources-r4b:${hapiVersion}")
    // caching is required, as without it the validator throws:
    //java.lang.RuntimeException: HAPI-2200: No Cache Service Providers found.
    // Choose between hapi-fhir-caching-caffeine (Default) and hapi-fhir-caching-guava (Android)
    implementation("ca.uhn.hapi.fhir:hapi-fhir-caching-caffeine:${hapiVersion}")
    //logging state of the art
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    //SQL timings
    implementation("net.ttddyy:datasource-proxy:1.10")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:junit-jupiter:${testContainerVersion}")
    testImplementation("org.testcontainers:postgresql:${testContainerVersion}")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}



tasks.test { useJUnitPlatform() }

application { mainClass.set("fhir.ApplicationKt") }
springBoot { mainClass.set("fhir.ApplicationKt") }
