import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

dependencies {
    implementation(rootProject.libs.kotlin.stdlib)
    compileOnly(rootProject.libs.paper.api)
}

sourceSets.main {
    kotlin.srcDir(layout.buildDirectory.dir("generated/src/main/kotlin"))
}

tasks.named("shadowJar", ShadowJar::class) {
    mergeServiceFiles()

    archiveFileName.set("${project.name}.jar")
}

tasks.named("compileKotlin") {
    dependsOn("generateArtifactsClass")
}


tasks.register("generateArtifactsClass") {
    group = "generation"
    description = "Generates a Kotlin class containing artifact information"

    val outputDir = layout.buildDirectory.dir("generated/src/main/kotlin/app/simplecloud/generated")
    val outputFile = outputDir.get().file("SimpleCloudArtifacts.kt")

    outputs.file(outputFile)

    doLast {
        outputDir.get().asFile.mkdirs()
        outputFile.asFile.writeText(
            """
            package app.simplecloud.generated
            
            object SimpleCloudArtifacts {
                val artifacts = listOf(
                    "${rootProject.libs.simplecloud.controller.get()}",
                    "${rootProject.libs.simplecloud.player.get()}"
                )
            }
            """.trimIndent()
        )
        println("Generated Artifacts.kt at ${outputFile.asFile.absolutePath}")
    }
}

// Add the generated source set to Kotlin compilation
sourceSets.main {
    kotlin.srcDir(layout.buildDirectory.dir("generated/src/main/kotlin"))
}
