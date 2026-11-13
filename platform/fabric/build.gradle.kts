import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("cloud-api.platform-conventions")
    alias(libs.plugins.fabric.loom)
}

val shade = configurations.create("shade")
val minecraftVersions = listOf(libs.versions.fabric.minecraft.get(), "26.3")

dependencies {
    minecraft("com.mojang:minecraft:${libs.versions.fabric.minecraft.get()}")

    implementation(libs.fabric.loader)

    implementation(project(":api"))

    shade(project(":api"))
}

tasks.named<ShadowJar>("shadowJar") {
    configurations = listOf(shade)
}

tasks.processResources {
    val properties = mapOf(
        "version" to project.version.toString(),
        "minecraftVersionRange" to ">=${minecraftVersions.first()} <=${minecraftVersions.last()}"
    )
    inputs.properties(properties)
    filesMatching("fabric.mod.json") {
        expand(properties)
    }
}

modrinth {
    gameVersions = minecraftVersions
    loaders.addAll("fabric", "quilt")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}
