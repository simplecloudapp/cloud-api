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
