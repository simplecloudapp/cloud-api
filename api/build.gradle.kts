import app.simplecloud.gradle.AddProtoDependencyToPom
import app.simplecloud.gradle.FixOpenApiGeneratedCode
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")

    `maven-publish`
    alias(libs.plugins.openapi.generator)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    implementation(rootProject.libs.jnats)
    implementation(rootProject.libs.controller.proto)
    implementation(rootProject.libs.player.proto)
    implementation(rootProject.libs.adventure.proto)
    implementation(rootProject.libs.okhttp)
    implementation(rootProject.libs.okhttp.logging)
    implementation(rootProject.libs.gson)
    implementation(rootProject.libs.gson.fire)
    implementation(rootProject.libs.jakarta.annotation)
    implementation(rootProject.libs.javax.annotation)

    implementation(rootProject.libs.adventure.api)
    implementation(rootProject.libs.adventure.gson)
    implementation(rootProject.libs.caffeine)

    testImplementation(rootProject.libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.named<ShadowJar>("shadowJar") {
    mergeServiceFiles()

    relocate("io.nats", "app.simplecloud.api.shaded.nats")
    relocate("com.google", "app.simplecloud.api.shaded.google")
    relocate("build.buf", "app.simplecloud.api.shaded.buf")
    relocate("okhttp3", "app.simplecloud.api.shaded.okhttp3")
    relocate("okio", "app.simplecloud.api.shaded.okio")
    relocate("io.gsonfire", "app.simplecloud.api.shaded.gsonfire")
    relocate("org.bouncycastle", "app.simplecloud.api.shaded.bouncycastle")
    relocate("org.intellij", "app.simplecloud.api.shaded.intellij")
    relocate("org.jetbrains", "app.simplecloud.api.shaded.jetbrains")
    relocate("kotlin", "app.simplecloud.api.shaded.kotlin")
    relocate("com.github.benmanes.caffeine", "app.simplecloud.api.shaded.caffeine")

    relocate("google", "app.simplecloud.api.shaded.google")
    relocate("native", "app.simplecloud.api.shaded.native")
    relocate("core", "app.simplecloud.api.shaded.core")

    archiveClassifier.set("")
}

tasks.named<Jar>("jar") {
    archiveClassifier.set("thin")
}

sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("generated/src/main/java"))
        }
    }
}

openApiGenerate {
    generatorName.set("java")
    remoteInputSpec.set("https://controller.simplecloud.app/swagger/doc.json")

    outputDir.set(layout.buildDirectory.dir("generated").map { it.asFile.absolutePath })

//    generateSupportingFiles.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
    generateApiTests.set(false)
    generateModelTests.set(false)

    apiPackage.set("app.simplecloud.api.web.apis")
    modelPackage.set("app.simplecloud.api.web.models")
    packageName.set("app.simplecloud.api.web")

    configOptions.set(
        mapOf(
            "library" to "okhttp-gson",
            "serializationLibrary" to "gson",
            "useJakartaEe" to "true",
            "disallowAdditionalPropertiesIfNotPresent" to "false"
        )
    )
}

val fixOpenApiGeneratedCode by tasks.registering(FixOpenApiGeneratedCode::class) {
    dependsOn("openApiGenerate")
    modelsDir.set(layout.buildDirectory.dir("generated/src/main/java/app/simplecloud/api/web/models"))
    markerFile.set(layout.buildDirectory.file("generated/.fixOpenApiGeneratedCode"))
}

val prepareGeneratedSources by tasks.registering {
    group = "build"
    description = "Generates and patches OpenAPI sources used by the API module."
    dependsOn(fixOpenApiGeneratedCode)
}

tasks.compileJava {
    dependsOn(prepareGeneratedSources)
}

tasks.named<Javadoc>("javadoc") {
    dependsOn(prepareGeneratedSources)
    isFailOnError = false
    options {
        (this as StandardJavadocDocletOptions).apply {
            addBooleanOption("Xdoclint:none", true)
            addBooleanOption("quiet", true)
            addStringOption("Xmaxerrs", "10000")
            addStringOption("Xmaxwarns", "10000")
        }
    }
}

tasks.named<Jar>("sourcesJar") {
    dependsOn(prepareGeneratedSources)
}

val controllerProtoDependencyVersion = rootProject.libs.controller.proto.get().version.toString()

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(tasks.named<ShadowJar>("shadowJar")) {
                classifier = ""
            }

            artifact(tasks.named<Jar>("javadocJar"))
            artifact(tasks.named<Jar>("sourcesJar"))

            pom {
                name.set("SimpleCloud API")
                description.set("Cloud API for SimpleCloud")
                url.set("https://github.com/simplecloudapp/cloud-api")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("simplecloud")
                        name.set("SimpleCloud Team")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/simplecloudapp/cloud-api.git")
                    developerConnection.set("scm:git:ssh://github.com/simplecloudapp/cloud-api.git")
                    url.set("https://github.com/simplecloudapp/cloud-api")
                }

                // Note: io.nats, okhttp3, gson, and gsonfire are not added because they're shaded.
                withXml(AddProtoDependencyToPom(controllerProtoDependencyVersion))
            }
        }
    }
}

//signing {
//    val isSnapshot = project.version.toString().contains("dev")
//    if (!isSnapshot) {
//        sign(publishing.publications["maven"])
//        useGpgCmd()
//    }
//}

tasks.register<Javadoc>("generateJavadocSite") {
    dependsOn("compileJava")
    source = sourceSets["main"].allJava
    classpath = configurations["compileClasspath"]
    setDestinationDir(file("$projectDir/docs/javadoc"))
    isFailOnError = false

    options {
        (this as StandardJavadocDocletOptions).apply {
            links("https://docs.oracle.com/en/java/javase/21/docs/api/")
            addBooleanOption("html5", true)
            addStringOption("Xmaxerrs", "10000")
            addStringOption("Xmaxwarns", "10000")
        }
    }
}
repositories {
    mavenCentral()
}
