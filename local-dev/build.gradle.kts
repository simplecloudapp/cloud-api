plugins {
    id("cloud-api.base-conventions")
    id("application")
}

description = "Local-only scratch module for manual API testing"

dependencies {
    implementation(project(":api"))
}

application {
    mainClass = providers.gradleProperty("localDevMainClass").orElse("app.simplecloud.api.Test")
}
