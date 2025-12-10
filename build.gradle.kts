plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    id("com.google.cloud.tools.jib") version "3.4.4"
    application
    jacoco
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
val kotest_version: String by project

dependencies {
    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:$kotlin_tg_bot_version")
    testImplementation(kotlin("test"))
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinx_serialization_version")
    
    // Kotest for property-based testing
    testImplementation("io.kotest:kotest-runner-junit5:$kotest_version")
    testImplementation("io.kotest:kotest-assertions-core:$kotest_version")
    testImplementation("io.kotest:kotest-property:$kotest_version")
    
    // Mockito for mocking
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.mockito:mockito-core:5.18.0")
    
    // Ktor mock engine for testing
    testImplementation("io.ktor:ktor-client-mock:$ktor_version")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(23)
}

application {
    mainClass.set("jbaru.ch.telegram.hubitat.MainKt")
}

jacoco {
    toolVersion = "0.8.13"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "BRANCH"
                minimum = "0.75".toBigDecimal()
            }
        }
    }
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
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