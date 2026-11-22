package app.simplecloud.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.zip.ZipFile

@CacheableTask
abstract class GenerateNativeImageMetadata : DefaultTask() {

    @get:Classpath
    abstract val classesDirs: ConfigurableFileCollection

    @get:Classpath
    abstract val dependencies: ConfigurableFileCollection

    @get:Input
    abstract val metadataPath: Property<String>

    @get:Input
    abstract val reflectiveTypes: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val entries = sortedMapOf<String, Access>()

        classesDirs.files.filter { it.isDirectory }.forEach { dir ->
            dir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".class") }
                .map { it.relativeTo(dir).invariantSeparatorsPath.removeSuffix(".class").replace('/', '.') }
                .forEach { className ->
                    when {
                        className.startsWith(OPENAPI_PACKAGE) -> entries.grant(className, Access.FULL)
                        ANONYMOUS_CLASS.containsMatchIn(className) -> entries.grant(className, Access.TYPE)
                    }
                }
        }

        dependencies.files.filter { it.isFile && it.name.endsWith(".jar") }.forEach { jar ->
            jarClassNames(jar).forEach { className ->
                when {
                    className.startsWith(PROTO_PACKAGE) && !className.endsWith("OrBuilder") ->
                        entries.grant(Relocations.relocateClass(className), Access.FIELDS)
                    CAFFEINE_GENERATED_CLASS.matches(className) ->
                        entries.grant(Relocations.relocateClass(className), Access.FIELDS_AND_CONSTRUCTORS)
                }
            }
        }

        reflectiveTypes.get().forEach { entries.grant(Relocations.relocateClass(it), Access.FULL) }
        NATS_DATA_PORTS.forEach { entries.grant(Relocations.relocateClass(it), Access.CONSTRUCTORS) }
        OKHTTP_KOTLIN_TYPES.forEach { entries.grant(Relocations.relocateClass(it), Access.FULL) }

        val reflection = entries.map { (type, access) -> access.json(type) } + JDK_ENTRIES
        val resources = OKHTTP_RESOURCES
            .flatMap { listOf(it, Relocations.relocatePath(it)) }
            .distinct()
            .map { """    { "glob": "$it" }""" }

        val target = outputDir.get().asFile.resolve(metadataPath.get())
        target.deleteRecursively()
        target.mkdirs()
        target.resolve("reachability-metadata.json").writeText(
            buildString {
                appendLine("{")
                appendLine("  \"reflection\": [")
                appendLine(reflection.joinToString(",\n"))
                appendLine("  ],")
                appendLine("  \"resources\": [")
                appendLine(resources.joinToString(",\n"))
                appendLine("  ]")
                appendLine("}")
            },
        )
        target.resolve("native-image.properties").writeText(
            "Args = --enable-http --enable-https --features=${Relocations.relocateClass(OKHTTP_FEATURE)}\n",
        )
    }

    private fun jarClassNames(jar: File): List<String> = ZipFile(jar).use { zip ->
        zip.entries().asSequence()
            .map { it.name }
            .filter { it.endsWith(".class") && !it.startsWith("META-INF/") }
            .map { it.removeSuffix(".class").replace('/', '.') }
            .toList()
    }

    private fun MutableMap<String, Access>.grant(type: String, access: Access) {
        merge(type, access) { old, new -> if (new.ordinal > old.ordinal) new else old }
    }

    enum class Access(private val flags: String) {
        TYPE(""),
        CONSTRUCTORS(""", "allDeclaredConstructors": true"""),
        FIELDS(""", "allDeclaredFields": true"""),
        FIELDS_AND_CONSTRUCTORS(""", "allDeclaredFields": true, "allDeclaredConstructors": true"""),
        FULL(
            """, "allDeclaredFields": true, "allDeclaredConstructors": true, "allDeclaredMethods": true, "unsafeAllocated": true""",
        ),
        ;

        fun json(type: String): String = """    { "type": "$type"$flags }"""
    }

    private companion object {
        const val OPENAPI_PACKAGE = "app.simplecloud.api.web."
        const val PROTO_PACKAGE = "build.buf.gen."
        const val OKHTTP_FEATURE = "okhttp3.internal.graal.OkHttpFeature"

        val ANONYMOUS_CLASS = Regex("""\$\d+$""")
        val CAFFEINE_GENERATED_CLASS = Regex("""^com\.github\.benmanes\.caffeine\.cache\.[A-Z]+$""")

        val NATS_DATA_PORTS = listOf(
            "io.nats.client.impl.SocketDataPort",
            "io.nats.client.impl.SocketDataPortWithWriteTimeout",
        )

        val OKHTTP_KOTLIN_TYPES = listOf(
            "kotlin.internal.jdk8.JDK8PlatformImplementations",
            "kotlin.KotlinVersion",
            "kotlin.KotlinVersion\$Companion",
        )

        val OKHTTP_RESOURCES = listOf(
            "okhttp3/internal/publicsuffix/PublicSuffixDatabase.list",
        )

        val JDK_ENTRIES = listOf(
            """    { "type": "java.nio.Buffer", "fields": [{ "name": "address" }] }""",
            """    { "type": "sun.misc.Unsafe", "allDeclaredFields": true, "allPublicMethods": true }""",
        )
    }
}
