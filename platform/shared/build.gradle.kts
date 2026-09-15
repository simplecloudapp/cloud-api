dependencies {
    compileOnly(project(":api"))
    compileOnly(rootProject.libs.adventure.api)
    compileOnly(rootProject.libs.luckperms)

    testImplementation(project(":api"))
    testImplementation(rootProject.libs.junit.jupiter)
    testImplementation(rootProject.libs.mockito.core)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(rootProject.libs.luckperms)
    testImplementation(rootProject.libs.adventure.api)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
