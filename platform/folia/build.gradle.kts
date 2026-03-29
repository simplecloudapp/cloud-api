dependencies {
    compileOnly(rootProject.libs.folia.api)
    compileOnly(rootProject.libs.jnats)
    compileOnly(rootProject.libs.adventure.proto)
    compileOnly(rootProject.libs.adventure.api)
    compileOnly(rootProject.libs.adventure.gson)
    implementation(project(":platform:shared"))
    implementation(project(":api")) {
        exclude(group = "net.kyori")
    }
}
