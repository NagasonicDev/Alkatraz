package me.nagasonic.alkatraz.gui.implementation.research;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ResearchGraphMenuLayoutTest {

    @Test
    void shippedNodesAvoidDefaultPanArrowCells() {
        Set<Integer> arrowColumns = Set.of(-4, 0, 4);
        InputStream in = getClass().getResourceAsStream("/research.yml");
        assertNotNull(in, "research.yml must be on the test classpath");

        Map<String, Object> root = new Yaml().load(in);
        Map<?, ?> nodes = (Map<?, ?>) root.get("nodes");
        assertNotNull(nodes, "research.yml must contain nodes");

        for (Map.Entry<?, ?> entry : nodes.entrySet()) {
            Map<?, ?> node = (Map<?, ?>) entry.getValue();
            Map<?, ?> position = (Map<?, ?>) node.get("position");
            if (position == null) continue;
            int x = ((Number) position.get("x")).intValue();
            int y = ((Number) position.get("y")).intValue();
            assertFalse(y == 2 && arrowColumns.contains(x),
                    entry.getKey() + " sits on a pan arrow in the default view (" + x + "," + y + ")");
        }
    }
}
