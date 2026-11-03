plugins {
    id("cloud-api.platform-conventions")
}

dependencies {
    implementation(project(":platform:shared"))
    compileOnly(libs.velocity)
    compileOnly(libs.luckperms)
    compileOnly(libs.jnats)
    annotationProcessor(libs.velocity)
    compileOnly(libs.adventure.api)
    implementation(project(":api")) {
        exclude(group = "net.kyori")
    }

    testImplementation(libs.mockito.core)
    testImplementation(libs.velocity)
}

modrinth {
    loaders.add("velocity")
}
