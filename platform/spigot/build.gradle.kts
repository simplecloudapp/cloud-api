plugins {
    id("cloud-api.platform-conventions")
}

dependencies {
    compileOnly(libs.paper.api)
    implementation(project(":api"))
}

modrinth {
    loaders.add("spigot")
}
