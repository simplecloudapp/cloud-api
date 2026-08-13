import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.neoforge.userdev)
    alias(libs.plugins.minotaur)
}

val shade = configurations.create("shade")

dependencies {
    implementation(rootProject.libs.neoforge)
    compileOnly(rootProject.libs.jnats)
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

tasks.named("assemble") {
    dependsOn(tasks.shadowJar)
}

tasks.withType<JavaExec>().configureEach {
    if (name == "runServer") {
        standardInput = System.`in`
    }
}

tasks.processResources {
    val properties = mapOf(
        "version" to project.version.toString(),
        "minecraftVersion" to "26.2",
        "neoForgeVersion" to rootProject.libs.versions.neoforge.get()
    )
    inputs.properties(properties)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(properties)
    }
}

modrinth {
    token.set(project.findProperty("modrinthToken") as String? ?: System.getenv("MODRINTH_TOKEN"))
    projectId.set("JCJKZvY2")
    versionNumber.set(rootProject.extra["modrinthVersion"] as String)
    versionType.set("release")
    uploadFile.set(tasks.shadowJar)
    gameVersions.add("26.2")
    loaders.add("neoforge")
    changelog.set("https://docs.simplecloud.app/changelog")
    syncBodyFrom.set(rootProject.file("README.md").readText())
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}
