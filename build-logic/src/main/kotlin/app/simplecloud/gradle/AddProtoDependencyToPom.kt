package app.simplecloud.gradle

import groovy.util.Node
import groovy.util.NodeList
import org.gradle.api.Action
import org.gradle.api.XmlProvider
import java.io.Serializable

class AddProtoDependencyToPom(
    private val controllerProtoDependencyVersion: String
) : Action<XmlProvider>, Serializable {

    override fun execute(xml: XmlProvider) {
        val root = xml.asNode()
        val dependencies = root.get("dependencies") as NodeList
        val dependenciesNode = if (dependencies.isEmpty()) {
            root.appendNode("dependencies")
        } else {
            dependencies[0] as Node
        }

        dependenciesNode.appendNode("dependency").apply {
            appendNode("groupId", "build.buf.gen")
            appendNode("artifactId", "simplecloud_controller_protocolbuffers_java_lite")
            appendNode("version", controllerProtoDependencyVersion)
            appendNode("scope", "compile")
        }
    }

    private companion object {
        private const val serialVersionUID = 1L
    }
}
