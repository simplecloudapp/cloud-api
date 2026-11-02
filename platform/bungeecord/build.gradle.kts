plugins {
    id("cloud-api.platform-conventions")
}

dependencies {
    compileOnly(libs.bungeecord)
    compileOnly(libs.luckperms)
    compileOnly(libs.adventure.api)
    implementation(libs.adventure.bungeecord)
    implementation(project(":platform:shared"))
    implementation(project(":api")) {
        exclude(group = "net.kyori")
    }

    testImplementation(libs.mockito.core)
    testImplementation(libs.bungeecord)
}

modrinth {
    loaders.addAll("bungeecord", "waterfall")
}
