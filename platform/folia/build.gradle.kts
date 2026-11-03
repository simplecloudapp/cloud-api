plugins {
    id("cloud-api.platform-conventions")
}

dependencies {
    compileOnly(libs.folia.api)
    compileOnly(libs.jnats)
    compileOnly(libs.adventure.proto)
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.gson)
    implementation(project(":platform:shared"))
    implementation(project(":api")) {
        exclude(group = "net.kyori")
    }
}

modrinth {
    loaders.add("folia")
}
