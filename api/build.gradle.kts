import app.simplecloud.gradle.AddProtoDependencyToPom
import app.simplecloud.gradle.FixOpenApiGeneratedCode
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("cloud-api.shadow-conventions")
    alias(libs.plugins.openapi.generator)
}

java {
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    implementation(libs.jnats)
    implementation(libs.controller.proto)
    implementation(libs.player.proto)
    implementation(libs.adventure.proto)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    implementation(libs.gson.fire)
    implementation(libs.jakarta.annotation)

    implementation(libs.adventure.api)
    implementation(libs.adventure.gson)
    implementation(libs.caffeine)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.tls)
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier = ""
}

tasks.named<Jar>("jar") {
    archiveClassifier = "thin"
}

sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("generated/src/main/java"))
        }
    }
}

openApiGenerate {
    generatorName = "java"
    remoteInputSpec = "https://controller.simplecloud.app/swagger/doc.json"

    outputDir = layout.buildDirectory.dir("generated")

    generateApiDocumentation = false
    generateModelDocumentation = false
    generateApiTests = false
    generateModelTests = false

    apiPackage = "app.simplecloud.api.web.apis"
    modelPackage = "app.simplecloud.api.web.models"
    packageName = "app.simplecloud.api.web"

    configOptions = mapOf(
        "library" to "okhttp-gson",
        "serializationLibrary" to "gson",
        "useJakartaEe" to "true",
        "disallowAdditionalPropertiesIfNotPresent" to "false"
    )
}

tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("generateGoClient") {
    group = "build"
    description = "Generates the standalone Go client from the controller OpenAPI document."
    dependsOn("openApiGenerate")

    generatorName = "go"
    inputSpec = layout.buildDirectory.file("generated/api/openapi.yaml")
    outputDir = isolated.rootProject.projectDirectory.dir("go/generated")
    packageName = "generated"

    generateApiDocumentation = false
    generateModelDocumentation = false
    generateApiTests = false
    generateModelTests = false

    configOptions = mapOf(
        "enumClassPrefix" to "true",
        "generateInterfaces" to "true",
        "packageVersion" to version.toString(),
        "withGoMod" to "false"
    )
}

val fixOpenApiGeneratedCode = tasks.register<FixOpenApiGeneratedCode>("fixOpenApiGeneratedCode") {
    dependsOn("openApiGenerate")
    modelsDir = layout.buildDirectory.dir("generated/src/main/java/app/simplecloud/api/web/models")
    markerFile = layout.buildDirectory.file("generated/.fixOpenApiGeneratedCode")
}

val prepareGeneratedSources = tasks.register("prepareGeneratedSources") {
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
    val javadocOptions = options as StandardJavadocDocletOptions
    javadocOptions.addBooleanOption("Xdoclint:none", true)
    javadocOptions.addBooleanOption("quiet", true)
    javadocOptions.addStringOption("Xmaxerrs", "10000")
    javadocOptions.addStringOption("Xmaxwarns", "10000")
}

tasks.named<Jar>("sourcesJar") {
    dependsOn(prepareGeneratedSources)
}

val controllerProtoDependencyVersion = libs.controller.proto.get().version.toString()

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(tasks.named<ShadowJar>("shadowJar"))

            artifact(tasks.named<Jar>("javadocJar"))
            artifact(tasks.named<Jar>("sourcesJar"))

            pom {
                name = "SimpleCloud API"
                description = "Cloud API for SimpleCloud"
                url = "https://github.com/simplecloudapp/cloud-api"

                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }

                developers {
                    developer {
                        id = "simplecloud"
                        name = "SimpleCloud Team"
                    }
                }

                scm {
                    connection = "scm:git:git://github.com/simplecloudapp/cloud-api.git"
                    developerConnection = "scm:git:ssh://github.com/simplecloudapp/cloud-api.git"
                    url = "https://github.com/simplecloudapp/cloud-api"
                }

                // Note: io.nats, okhttp3, gson, and gsonfire are not added because they're shaded.
                withXml(AddProtoDependencyToPom(controllerProtoDependencyVersion))
            }
        }
    }
}
