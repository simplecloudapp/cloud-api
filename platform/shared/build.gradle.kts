plugins {
    id("cloud-api.base-conventions")
}

dependencies {
    compileOnly(project(":api"))
    compileOnly(libs.adventure.api)
    compileOnly(libs.luckperms)

    testImplementation(project(":api"))
    testImplementation(libs.mockito.core)
    testImplementation(libs.luckperms)
    testImplementation(libs.adventure.api)
}
