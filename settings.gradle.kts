pluginManagement {
    plugins {
        kotlin("jvm") version "2.2.20"
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
//pluginManagement {
//    plugins {
//        kotlin("jvm") version "2.2.20"
//    }
//}
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
    "platform:folia",
    "platform:bungeecord",
    "platform:velocity"
)
