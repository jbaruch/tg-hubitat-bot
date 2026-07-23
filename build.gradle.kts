buildscript {
    configurations.classpath {
        resolutionStrategy {
            // The jib-gradle-plugin bundles vulnerable build-time deps
            // (jackson 2.15.2, commons-lang3 3.14.0). These run only during image
            // build and never ship in the container, but force patched versions
            // to clear the Dependabot alerts against the plugin classpath.
            force(
                "com.fasterxml.jackson.core:jackson-databind:2.18.9",
                "com.fasterxml.jackson.core:jackson-core:2.18.9",
                "org.apache.commons:commons-lang3:3.18.0"
            )
        }
    }
}

plugins {
    kotlin("jvm") version "2.3.0-RC3"
    kotlin("plugin.serialization") version "2.3.0-RC3"
    id("com.google.cloud.tools.jib") version "3.5.1"
    application
    jacoco
    id("dev.detekt") version "2.0.0-alpha.5"
}

group = "jbaru.ch"
version = "3.12"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
    maven("https://jitpack.io")
}

val ktor_version: String by project
val logback_version: String by project
val kotlin_tg_bot_version: String by project
val kotlinx_serialization_version: String by project
val kotlinx_coroutines_version: String by project
val kotest_version: String by project

dependencies {
    constraints {
        // gson is pulled transitively by the telegram bot (retrofit converter-gson)
        // at 2.8.5, which has a known deserialization DoS (CVE fixed in 2.8.9).
        // Force a patched version onto the runtime classpath.
        implementation("com.google.code.gson:gson:2.11.0") {
            because("2.8.5 (pulled via retrofit converter-gson) is vulnerable; 2.8.9+ is patched")
        }
    }

    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:$kotlin_tg_bot_version")
    testImplementation(kotlin("test"))
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinx_serialization_version")
    // Used directly (async/awaitAll/Semaphore in the sensor and firmware
    // fan-outs), so declared explicitly rather than relied on transitively.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinx_coroutines_version")
    
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
    jvmToolchain(25)
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
        // High coverage for core business logic classes
        rule {
            element = "CLASS"
            includes = listOf(
                "jbaru.ch.telegram.hubitat.CommandHandlers",
                "jbaru.ch.telegram.hubitat.HubOperations",
                "jbaru.ch.telegram.hubitat.DeviceAbbreviator",
                "jbaru.ch.telegram.hubitat.StringUtils*"
            )
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
        rule {
            element = "CLASS"
            includes = listOf(
                "jbaru.ch.telegram.hubitat.ModeOperations"
            )
            limit {
                minimum = "0.74".toBigDecimal()
            }
        }
        rule {
            element = "CLASS"
            includes = listOf(
                "jbaru.ch.telegram.hubitat.CommandHandlers",
                "jbaru.ch.telegram.hubitat.HubOperations",
                "jbaru.ch.telegram.hubitat.DeviceAbbreviator",
                "jbaru.ch.telegram.hubitat.ModeOperations"
            )
            limit {
                counter = "BRANCH"
                minimum = "0.70".toBigDecimal()
            }
        }
        
        // Medium coverage for supporting classes
        rule {
            element = "CLASS"
            includes = listOf(
                "jbaru.ch.telegram.hubitat.DeviceManager"
            )
            limit {
                minimum = "0.67".toBigDecimal()
            }
        }
        rule {
            element = "CLASS"
            includes = listOf(
                "jbaru.ch.telegram.hubitat.NetworkClient"
            )
            limit {
                minimum = "0.33".toBigDecimal()
            }
        }
        
        // Lower coverage for data classes
        rule {
            element = "CLASS"
            includes = listOf(
                "jbaru.ch.telegram.hubitat.ModeInfo*"
            )
            limit {
                minimum = "0.50".toBigDecimal()
            }
        }
        
        // Lower coverage for model package (auto-generated serialization)
        rule {
            element = "PACKAGE"
            includes = listOf("jbaru.ch.telegram.hubitat.model")
            limit {
                minimum = "0.30".toBigDecimal()
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
    from {
        image = "eclipse-temurin:25-jre"
    }
    to {
        image = "jbaru.ch/tg-hubitat-bot"
        tags = setOf(version.toString(), "latest")
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
    outputPaths {
        tar = "${layout.buildDirectory.get()}/tg-hubitat-bot-${version}-docker-image.tar"
    }
}

// Configure task dependencies for Docker image building
tasks.named("jibDockerBuild") {
    dependsOn(tasks.named("build"))
}

tasks.named("jibBuildTar") {
    dependsOn(tasks.named("jibDockerBuild"))
}


// Static-analysis tooling. detekt 2.0.0-alpha.5 supports the JVM 25 toolchain
// (1.23.x caps at jvmTarget 22). Run on demand with `./gradlew detekt`.
// No CI gate yet: per language-diagnostics "Adopting on a Dirty Tree", the gate
// is wired only once the tree reports zero findings — fixes land first, in
// separate PRs, then a final PR adds the CI step.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt/detekt.yml"))
}
