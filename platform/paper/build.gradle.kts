dependencies {
    compileOnly(rootProject.libs.paper.api)
    implementation(project(":platform:shared"))
    implementation(project(":api"))
}

sourceSets.main {
    java.srcDir(layout.buildDirectory.dir("generated/src/main/java"))
}

tasks.register("generateArtifactsClass") {
    group = "generation"
    description = "Generates a Java class containing artifact information"

    val outputDir = layout.buildDirectory.dir("generated/src/main/java/app/simplecloud/generated")
    val outputFile = outputDir.get().file("SimpleCloudArtifacts.java")

    outputs.file(outputFile)

    doLast {
        outputDir.get().asFile.mkdirs()
        outputFile.asFile.writeText(
            """
            package app.simplecloud.generated;
            
            import java.util.List;
            
            public class SimpleCloudArtifacts {
                public static final List<String> artifacts = List.of(
                    "${rootProject.libs.controller.proto.get()}",
                    "${rootProject.libs.jnats.get()}"
                );
            }
            """.trimIndent()
        )
        println("Generated Artifacts.java at ${outputFile.asFile.absolutePath}")
    }
}

tasks.named("compileJava") {
    dependsOn("generateArtifactsClass")
}

sourceSets.main {
    java.srcDir(layout.buildDirectory.dir("generated/src/main/java"))
}

