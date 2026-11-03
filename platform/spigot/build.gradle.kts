plugins {
    id("cloud-api.platform-conventions")
}

dependencies {
    api(project(":platform:shared"))
    compileOnly(libs.paper.api)
    implementation(project(":api"))
}

modrinth {
    loaders.add("spigot")
}
