plugins {
    val kotlinVersion = "1.4.21"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion

    id("net.mamoe.mirai-console") version "2.0-M2" // mirai-console version
}

mirai {
    coreVersion = "2.0-M2" // mirai-core version
}

group = "org.lyc"
version = "0.1.0"

repositories {
    mavenLocal()
    mavenCentral()
    jcenter()
    maven("https://dl.bintray.com/kotlin/kotlin-eap")
}

kotlin {
    sourceSets {
        main {
            dependencies {
                implementation("com.squareup.moshi:moshi-kotlin:1.11.0")
            }
        }
    }
}