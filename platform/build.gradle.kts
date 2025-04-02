import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

tasks.named("shadowJar", ShadowJar::class).configure {
    enabled = false
}
