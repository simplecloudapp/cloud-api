import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

dependencies {
    implementation(rootProject.libs.kotlin.stdlib)
    implementation(rootProject.libs.kotlin.reflect)
    implementation(rootProject.libs.kotlin.coroutines)
    compileOnly(rootProject.libs.bungeecord)
    api(rootProject.libs.simplecloud.controller) {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
    }
    api(rootProject.libs.simplecloud.player) {
        exclude(group = "net.kyori")
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
    }
}

tasks.named("shadowJar", ShadowJar::class) {
    mergeServiceFiles()

    relocate("com.google.protobuf", "app.simplecloud.relocate.google.protobuf")
    relocate("com.google.common", "app.simplecloud.relocate.google.common")
    relocate("io.grpc", "app.simplecloud.relocate.io.grpc")

    archiveFileName.set("${rootProject.name}.jar")
}