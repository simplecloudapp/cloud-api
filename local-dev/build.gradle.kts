plugins {
    application
}

description = "Local-only scratch module for manual API testing"

dependencies {
    implementation(project(":api"))

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

application {
    mainClass.set(
        providers.gradleProperty("localDevMainClass")
            .orElse("app.simplecloud.api.Test")
    )
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<PublishToMavenLocal>().configureEach {
    enabled = false
}

tasks.withType<PublishToMavenRepository>().configureEach {
    enabled = false
}
