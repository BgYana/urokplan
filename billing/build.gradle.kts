plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":platform:database"))
    implementation(project(":identity"))
    implementation(project(":relationships"))
    implementation(project(":courses"))
    implementation(project(":scheduling"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.serialization.json)

    testImplementation(platform(libs.junit.bom))
    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.server.content.negotiation)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.logback)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("api.version", "1.40")
}
