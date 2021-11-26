package org.janusgraph.diskstorage.cql;

import eu.rekawek.toxiproxy.model.ToxicDirection;
import org.janusgraph.JanusGraphCassandraContainer;
import org.janusgraph.diskstorage.BackendException;
import org.janusgraph.diskstorage.configuration.ModifiableConfiguration;
import org.janusgraph.diskstorage.keycolumnvalue.KeyColumnValueStore;
import org.janusgraph.diskstorage.keycolumnvalue.KeySliceQuery;
import org.janusgraph.diskstorage.keycolumnvalue.StoreTransaction;
import org.janusgraph.diskstorage.util.StandardBaseTransactionConfig;
import org.janusgraph.diskstorage.util.StaticArrayBuffer;
import org.janusgraph.diskstorage.util.time.TimestampProviders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.time.Duration;

import static org.janusgraph.graphdb.configuration.GraphDatabaseConfiguration.CONNECTION_TIMEOUT;
import static org.janusgraph.graphdb.configuration.GraphDatabaseConfiguration.STORAGE_HOSTS;
import static org.janusgraph.graphdb.configuration.GraphDatabaseConfiguration.STORAGE_PORT;

@ExtendWith(MockitoExtension.class)
@Testcontainers
public class CQLStoreReadTimeoutTest {

    public static final Network network = Network.newNetwork();

    @Container
    public static final JanusGraphCassandraContainer cqlContainer = new JanusGraphCassandraContainer(true)
        .withExposedPorts(CassandraContainer.CQL_PORT)
        .withNetwork(network);

    @Container
    public static final ToxiproxyContainer toxiProxyContainer = new ToxiproxyContainer("shopify/toxiproxy:2.1.0")
        .withNetwork(network);

    private ModifiableConfiguration getBaseStorageConfiguration() {
        return cqlContainer.getConfiguration(getClass().getSimpleName());
    }

    @Test
    public void test() throws IOException, BackendException {
        // Setup toxiproxy
        final ToxiproxyContainer.ContainerProxy proxy = toxiProxyContainer.getProxy(cqlContainer, CassandraContainer.CQL_PORT);

        // Forces all requests to go through toxiproxy
        final CQLStoreManager cqlStoreManager = new CQLStoreManager(
            getBaseStorageConfiguration()
                .set(CONNECTION_TIMEOUT, Duration.ofSeconds(5))
                .set(STORAGE_PORT, proxy.getProxyPort())
                .set(STORAGE_HOSTS, new String[]{proxy.getContainerIpAddress()})
        );
        final KeyColumnValueStore cqlStore = cqlStoreManager.openDatabase("testcf_read_timeout");

        // Make toxiproxy timeout on any request. See https://github.com/Shopify/toxiproxy#timeout
        proxy.toxics()
             .timeout("timeout1", ToxicDirection.DOWNSTREAM, 0L);

        // Send dummy request
        StoreTransaction txh = cqlStoreManager.beginTransaction(
            StandardBaseTransactionConfig.of(
                TimestampProviders.MICRO,
                cqlStoreManager.getFeatures().getKeyConsistentTxConfig()
            )
        );
        cqlStore.getSlice(
            new KeySliceQuery(
                StaticArrayBuffer.of("row".getBytes()),
                StaticArrayBuffer.of("0".getBytes()),
                StaticArrayBuffer.of("z".getBytes())
            ),
            txh
        );
    }
}


