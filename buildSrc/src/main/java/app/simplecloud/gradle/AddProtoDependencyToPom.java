package app.simplecloud.gradle;

import groovy.util.Node;
import groovy.util.NodeList;
import org.gradle.api.Action;
import org.gradle.api.XmlProvider;

import java.io.Serializable;

public class AddProtoDependencyToPom implements Action<XmlProvider>, Serializable {
    private static final long serialVersionUID = 1L;

    private final String controllerProtoDependencyVersion;

    public AddProtoDependencyToPom(String controllerProtoDependencyVersion) {
        this.controllerProtoDependencyVersion = controllerProtoDependencyVersion;
    }

    @Override
    public void execute(XmlProvider xml) {
        Node root = xml.asNode();
        NodeList dependencies = (NodeList) root.get("dependencies");
        Node dependenciesNode = dependencies.isEmpty()
            ? root.appendNode("dependencies")
            : (Node) dependencies.get(0);

        Node protoDep = dependenciesNode.appendNode("dependency");
        protoDep.appendNode("groupId", "build.buf.gen");
        protoDep.appendNode("artifactId", "simplecloud_controller_protocolbuffers_java_lite");
        protoDep.appendNode("version", controllerProtoDependencyVersion);
        protoDep.appendNode("scope", "compile");
    }
}
