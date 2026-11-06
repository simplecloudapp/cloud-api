plugins {
    id("cloud-api.base-conventions") apply false
    id("cloud-api.spotless-conventions")
}

allprojects {
    group = "app.simplecloud.api"
    version = "1.0.0"
}
