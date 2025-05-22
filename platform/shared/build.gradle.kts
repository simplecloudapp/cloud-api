dependencies {
    compileOnly(rootProject.libs.kotlin.stdlib)
    compileOnly(rootProject.libs.kotlin.reflect)
    compileOnly(rootProject.libs.kotlin.coroutines)
    compileOnly(rootProject.libs.simplecloud.controller) {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
    }
    compileOnly(rootProject.libs.simplecloud.player) {
        exclude(group = "net.kyori")
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
    }
}