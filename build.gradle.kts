plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.serialization") version "2.0.0"
    id("com.google.cloud.tools.jib") version "3.4.2"
}

group = "jbaru.ch"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

val ktor_version: String by project
val logback_version: String by project
val kotlin_tg_bot_version: String by project
val kotlinx_serialization_version: String by project

dependencies {
    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:$kotlin_tg_bot_version")
    testImplementation(kotlin("test"))
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinx_serialization_version")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

jib {
    to {
        image = "jbaru.ch/tg-hubitat-bot"
    }
    container {
        environment = mapOf(
            "BOT_TOKEN" to (project.findProperty("BOT_TOKEN") ?: "") as String,
            "MAKER_API_TOKEN" to (project.findProperty("MAKER_API_TOKEN") ?: "") as String,
            "MAKER_API_APP_ID" to (project.findProperty("MAKER_API_APP_ID") ?: "") as String,
            "CHAT_ID" to (project.findProperty("CHAT_ID") ?: "") as String,
            "DEFAULT_HUB_IP" to (project.findProperty("DEFAULT_HUB_IP") ?: "") as String
        )
    }
}