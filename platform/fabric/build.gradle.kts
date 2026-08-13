import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.minotaur)
}

val shade = configurations.create("shade")

dependencies {
    minecraft("com.mojang:minecraft:${rootProject.libs.versions.fabric.minecraft.get()}")

    implementation(rootProject.libs.fabric.loader)
    implementation(rootProject.libs.fabric.api)
    compileOnly(rootProject.libs.jnats)

    implementation(project(":platform:shared"))
    implementation(project(":api"))

    shade(project(":platform:shared"))
    shade(project(":api"))
}

tasks.named<ShadowJar>("shadowJar") {
    configurations = listOf(shade)
}

tasks.named("assemble") {
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    val modVersion = project.version.toString()
    inputs.property("version", modVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to modVersion)
    }
}

modrinth {
    token.set(project.findProperty("modrinthToken") as String? ?: System.getenv("MODRINTH_TOKEN"))
    projectId.set("JCJKZvY2")
    versionNumber.set(rootProject.extra["modrinthVersion"] as String)
    versionType.set("release")
    uploadFile.set(tasks.shadowJar)
    gameVersions.add("26.2")
    loaders.add("fabric")
    loaders.add("quilt")
    changelog.set("https://docs.simplecloud.app/changelog")
    syncBodyFrom.set(rootProject.file("README.md").readText())
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}
