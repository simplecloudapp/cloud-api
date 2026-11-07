pluginManagement {
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "cloud-api"

include(
    "api",
    "local-dev",
    "platform:shared",
    "platform:spigot",
    "platform:spigot-legacy",
    "platform:paper",
    "platform:bungeecord",
    "platform:velocity",
    "platform:fabric",
    "platform:neoforge",
    "platform:minestom",
)
