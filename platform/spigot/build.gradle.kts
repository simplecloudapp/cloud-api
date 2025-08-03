import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.minotaur)
}

dependencies {
    compileOnly(rootProject.libs.kotlin.stdlib)
    compileOnly(rootProject.libs.kotlin.reflect)
    compileOnly(rootProject.libs.kotlin.coroutines)
    api(project(":platform:shared"))
    compileOnly(rootProject.libs.paper.api)
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

    exclude("kotlin")
    exclude("kotlinx")
    mergeServiceFiles()

    relocate("com.google.protobuf", "app.simplecloud.relocate.google.protobuf")
    relocate("com.google.common", "app.simplecloud.relocate.google.common")
    relocate("io.grpc", "app.simplecloud.relocate.io.grpc")

    archiveFileName.set("${project.name}.jar")
}

modrinth {
    token.set(project.findProperty("modrinthToken") as String? ?: System.getenv("MODRINTH_TOKEN"))
    projectId.set("JCJKZvY2")
    versionNumber.set(rootProject.version.toString())
    versionType.set("beta")
    uploadFile.set(tasks.shadowJar)
    gameVersions.addAll(
        "1.20",
        "1.20.1",
        "1.20.2",
        "1.20.3",
        "1.20.4",
        "1.20.5",
        "1.20.6",
        "1.21",
        "1.21.1",
        "1.21.2",
        "1.21.3",
        "1.21.4",
        "1.21.5",
        "1.21.6",
        "1.21.7",
        "1.21.8",
    )
    loaders.add("spigot")
    loaders.add("paper")
    loaders.add("leaf")
    changelog.set("https://docs.simplecloud.app/changelog")
    syncBodyFrom.set(rootProject.file("README.md").readText())
}