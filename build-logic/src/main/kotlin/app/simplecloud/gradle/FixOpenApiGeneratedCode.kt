package app.simplecloud.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.regex.Pattern

abstract class FixOpenApiGeneratedCode : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val modelsDir: DirectoryProperty

    @get:OutputFile
    abstract val markerFile: RegularFileProperty

    @TaskAction
    fun fix() {
        modelsDir.get().asFile.listFiles()
            ?.filter { it.isFile && it.name.startsWith("V0") && it.name.endsWith(".java") }
            ?.forEach { file ->
                val content = file.readText()
                val fixed = WRITE_METHOD_PATTERN.matcher(content).replaceAll("\$2\n                    \$1")
                    .let { SET_INSTANCE_PATTERN.matcher(it).replaceAll("\$1\$3") }

                if (fixed != content) {
                    file.writeText(fixed)
                }
            }

        markerFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText("fixed\n")
        }
    }

    private companion object {
        private val WRITE_METHOD_PATTERN: Pattern = Pattern.compile(
            """(// check if the actual instance is of the type `Object`\s+if \(value\.getActualInstance\(\) instanceof Object\) \{[^}]+\}\s+)(// check if the actual instance is of the type `(\w+)`\s+if \(value\.getActualInstance\(\) instanceof \3\) \{[^}]+\})""",
            Pattern.DOTALL
        )

        private val SET_INSTANCE_PATTERN: Pattern = Pattern.compile(
            """(public void setActualInstance\(Object instance\) \{\s+)(if \(instance instanceof Object\) \{\s+super\.setActualInstance\(instance\);\s+return;\s+\}\s+)(if \(instance instanceof (\w+)\) \{)""",
            Pattern.DOTALL
        )
    }
}
