package app.simplecloud.gradle

object Relocations {

    private const val TARGET_PREFIX = "app.simplecloud.api.shaded"

    val packages: Map<String, String> = linkedMapOf(
        "io.nats" to "$TARGET_PREFIX.nats",
        "com.google" to "$TARGET_PREFIX.google",
        "build.buf" to "$TARGET_PREFIX.buf",
        "okhttp3" to "$TARGET_PREFIX.okhttp3",
        "okio" to "$TARGET_PREFIX.okio",
        "io.gsonfire" to "$TARGET_PREFIX.gsonfire",
        "org.bouncycastle" to "$TARGET_PREFIX.bouncycastle",
        "kotlin" to "$TARGET_PREFIX.kotlin",
        "com.github.benmanes.caffeine" to "$TARGET_PREFIX.caffeine",
        "google" to "$TARGET_PREFIX.google",
        "native" to "$TARGET_PREFIX.native",
        "core" to "$TARGET_PREFIX.core",
    )

    val excludes: Map<String, List<String>> = mapOf(
        "com.google" to listOf("com.google.inject.**"),
    )

    fun relocateClass(className: String): String = relocate(className, '.')

    fun relocatePath(path: String): String = relocate(path, '/')

    private fun relocate(name: String, separator: Char): String {
        val dotted = name.replace(separator, '.')
        val excluded = excludes.values.flatten().any { dotted.startsWith(it.removeSuffix("**")) }
        if (excluded) return name

        val source = packages.keys
            .filter { dotted.startsWith("$it.") }
            .maxByOrNull { it.length }
            ?: return name
        val target = packages.getValue(source)
        return target.replace('.', separator) + name.substring(source.length)
    }
}
