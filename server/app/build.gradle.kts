plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

dependencies {
    implementation(project(":platform:database"))
    implementation(project(":identity"))
    implementation(project(":relationships"))
    implementation(project(":courses"))
    implementation(project(":scheduling"))
    implementation(project(":billing"))
    implementation(project(":learning"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.swagger)
    implementation(libs.ktor.serialization.json)
    implementation(libs.logback)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

ktor {
    fatJar {
        archiveFileName.set("urokplan-server.jar")
    }
}
