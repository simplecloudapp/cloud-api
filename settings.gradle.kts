pluginManagement {
    plugins {
        kotlin("jvm") version "2.2.20"
    }
}
rootProject.name = "cloud-api"

include(
    "api",
//    "api-kotlin",
//    "api-kotlin-codegen",
//    "api-annotations",
    "platform:shared",
    "platform:spigot",
    "platform:spigot-legacy",
    "platform:paper",
    "platform:bungeecord",
    "platform:velocity"
)
