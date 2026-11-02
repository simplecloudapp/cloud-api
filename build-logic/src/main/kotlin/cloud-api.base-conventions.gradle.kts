import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.authentication.http.BasicAuthentication

plugins {
    id("java-library")
    id("maven-publish")
    id("com.gradleup.shadow")
}

val libs = the<LibrariesForLibs>()

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://repo.papermc.io/repository/maven-public")
    maven("https://repo.simplecloud.app/snapshots")
    maven("https://buf.build/gen/maven")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

publishing {
    repositories {
        maven {
            name = "simplecloud"
            url = uri("https://repo.simplecloud.app/snapshots/")
            credentials {
                username = providers.environmentVariable("SIMPLECLOUD_USERNAME").orNull
                password = providers.environmentVariable("SIMPLECLOUD_PASSWORD").orNull
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
}
