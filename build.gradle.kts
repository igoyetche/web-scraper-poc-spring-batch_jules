import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.22" // Define Kotlin version here
    id("org.springframework.boot") version "3.2.2"
    id("io.spring.dependency-management") version "1.1.4"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-batch")
    implementation("org.springframework.retry:spring-retry")
    implementation("org.springframework.cloud:spring-cloud-starter-task:3.1.0") // Specify a version for spring-cloud-starter-task
    implementation("org.jsoup:jsoup:1.17.2") // Specify a version for jsoup
    implementation("com.google.cloud:spring-cloud-gcp-starter-pubsub:5.0.0") // Specify a version for spring-cloud-gcp-starter-pubsub
    implementation("io.github.michaelruocco:spring-batch-mongodb:4.0.0")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:mongodb:1.19.3") // Specify a version for testcontainers-mongodb
    testImplementation("org.testcontainers:junit-jupiter:1.19.3") // Specify a version for testcontainers-junit-jupiter
    testImplementation(kotlin("test"))
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "21"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Spring Boot plugin configuration
springBoot {
    mainClass.set("com.example.articlescraper.ArticleScraperApplicationKt") // Set to the correct main class
}
