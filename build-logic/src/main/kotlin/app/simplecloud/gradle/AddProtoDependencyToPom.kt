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

        val dependency = dependenciesNode.appendNode("dependency")
        dependency.appendNode("groupId", "build.buf.gen")
        dependency.appendNode("artifactId", "simplecloud_controller_protocolbuffers_java_lite")
        dependency.appendNode("version", controllerProtoDependencyVersion)
        dependency.appendNode("scope", "compile")
    }
}
