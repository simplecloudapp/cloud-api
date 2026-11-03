import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("cloud-api.platform-conventions")
    alias(libs.plugins.neoforge.userdev)
}

val shade = configurations.create("shade")
val minecraftVersions = listOf("26.2", "26.3")

dependencies {
    implementation(libs.neoforge)
    implementation(project(":platform:shared"))
    implementation(project(":api"))

    shade(project(":platform:shared"))
    shade(project(":api"))
}

runs {
    register("server") {
        runType("server")
        argument("--nogui")
        workingDirectory(layout.projectDirectory.dir("run/server"))
        modSource(sourceSets.main.get())
    }
}

tasks.named<ShadowJar>("shadowJar") {
    configurations = listOf(shade)
}

tasks.withType<JavaExec>().configureEach {
    if (name == "runServer") {
        standardInput = System.`in`
    }
}

tasks.processResources {
    val properties = mapOf(
        "version" to project.version.toString(),
        "minecraftVersionRange" to "[${minecraftVersions.first()},${minecraftVersions.last()}]",
        "neoForgeVersion" to libs.versions.neoforge.get()
    )
    inputs.properties(properties)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(properties)
    }
}

modrinth {
    gameVersions = minecraftVersions
    loaders.add("neoforge")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}
