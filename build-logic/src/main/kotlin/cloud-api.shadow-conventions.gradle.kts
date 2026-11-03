import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("cloud-api.base-conventions")
}

tasks.named<ShadowJar>("shadowJar") {
    mergeServiceFiles()

    relocate("io.nats", "app.simplecloud.api.shaded.nats")
    relocate("com.google", "app.simplecloud.api.shaded.google") {
        exclude("com.google.inject.**")
    }
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
}
