// Copyright 2022 JanusGraph Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.janusgraph.core.es;

import static org.janusgraph.graphdb.configuration.GraphDatabaseConfiguration.GRAPH_NAME;
import static org.janusgraph.graphdb.configuration.GraphDatabaseConfiguration.INDEX_HOSTS;
import static org.janusgraph.graphdb.configuration.GraphDatabaseConfiguration.STORAGE_BACKEND;
import static org.janusgraph.graphdb.configuration.GraphDatabaseConfiguration.STORAGE_HOSTS;
import static org.janusgraph.graphdb.configuration.GraphDatabaseConfiguration.STORAGE_PORT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.configuration2.MapConfiguration;
import org.janusgraph.JanusGraphCassandraContainer;
import org.janusgraph.core.AbstractConfiguredGraphFactoryTest;
import org.janusgraph.core.ConfiguredGraphFactory;
import org.janusgraph.diskstorage.es.JanusGraphElasticsearchContainer;
import org.janusgraph.graphdb.database.StandardJanusGraph;
import org.janusgraph.util.system.ConfigurationUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class ConfiguredGraphFactoryLeakTest extends AbstractConfiguredGraphFactoryTest {

    @Container
    public static final JanusGraphElasticsearchContainer esContainer = new JanusGraphElasticsearchContainer();
    @Container
    public static final JanusGraphCassandraContainer cqlContainer = new JanusGraphCassandraContainer();

    protected MapConfiguration getManagementConfig() {
        final Map<String, Object> map = new HashMap<>();
        map.put(STORAGE_BACKEND.toStringWithoutRoot(), "cql");
        map.put(STORAGE_HOSTS.toStringWithoutRoot(), cqlContainer.getContainerIpAddress());
        map.put(STORAGE_PORT.toStringWithoutRoot(), cqlContainer.getMappedCQLPort());
        /** {@link INDEX_HOSTS} and {@link INDEX_PORT} are **unset** at the ManagementConfig level
         so their default values should be used. */
        return ConfigurationUtil.loadMapConfiguration(map);
    }

    protected MapConfiguration getTemplateConfig() {
        return getManagementConfig();
    }

    protected MapConfiguration getGraphConfig() {
        final Map<String, Object> map = getTemplateConfig().getMap();
        map.put(GRAPH_NAME.toStringWithoutRoot(), "graph1");

        /** {@link INDEX_HOSTS} and {@link INDEX_PORT} are **set** at the Graph Config level */
        /** We set {@link INDEX_HOSTS} to "localhost" so it can be distinguished from {@link INDEX_HOSTS.defaultValue},
         which is an ip address, typically 127.0.0.1 */
        map.put("index.search.hostname", "localhost");
        map.put("index.search.port", esContainer.getPort());

        return ConfigurationUtil.loadMapConfiguration(map);
    }

    @Test
    public void updateConfigurationTest() throws Exception {
        final MapConfiguration graphConfig = getGraphConfig();
        final String graphName = graphConfig.getString(GRAPH_NAME.toStringWithoutRoot());

        try {
            ConfiguredGraphFactory.createConfiguration(graphConfig);
            StandardJanusGraph graph = (StandardJanusGraph) ConfiguredGraphFactory.open(graphName);
            Assertions.assertNotNull(graph);

            /** {@link INDEX_HOSTS} is correctly set in the Graph configuration */
            assertEquals("localhost", ConfiguredGraphFactory.getConfiguration(graphName).get("index.search.hostname"));

            /** {@link INDEX_HOSTS} shouldn't be set in the Management configuration */
            // FAILS with [127.0.0.1] != [localhost]
            assertEquals(Arrays.toString(INDEX_HOSTS.getDefaultValue()), graph.openManagement().get("index.search.hostname"));
        } finally {
            ConfiguredGraphFactory.removeConfiguration(graphName);
            ConfiguredGraphFactory.close(graphName);
        }
    }
}
