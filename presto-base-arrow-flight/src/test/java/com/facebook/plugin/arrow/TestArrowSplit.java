/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.plugin.arrow;

import com.facebook.presto.spi.HostAddress;
import com.facebook.presto.spi.schedule.NodeSelectionStrategy;
import org.apache.arrow.flight.FlightEndpoint;
import org.apache.arrow.flight.Location;
import org.apache.arrow.flight.Ticket;
import org.testng.annotations.Test;

import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

@Test(singleThreaded = true)
public class TestArrowSplit
{
    private static final String TEST_SCHEMA = "testSchema";
    private static final String TEST_TABLE = "testTable";

    @Test
    public void testConstructorAndGetters()
    {
        FlightEndpoint flightEndpoint = createTestEndpoint();
        ArrowSplit arrowSplit = createTestSplit(TEST_SCHEMA, TEST_TABLE);

        // Test that the constructor correctly initializes fields
        assertEquals(arrowSplit.getSchemaName(), TEST_SCHEMA, "Schema name should match.");
        assertEquals(arrowSplit.getTableName(), TEST_TABLE, "Table name should match.");
        assertEquals(arrowSplit.getFlightEndpointBytes(), flightEndpoint.serialize().array(), "Byte array should match");
    }

    @Test
    public void testNodeSelectionStrategy()
    {
        ArrowSplit arrowSplit = createTestSplit(TEST_SCHEMA, TEST_TABLE);

        // Test that the node selection strategy is NO_PREFERENCE
        assertEquals(arrowSplit.getNodeSelectionStrategy(), NodeSelectionStrategy.NO_PREFERENCE, "Node selection strategy should be NO_PREFERENCE.");
    }

    @Test
    public void testGetPreferredNodes()
    {
        ArrowSplit arrowSplit = createTestSplit(TEST_SCHEMA, TEST_TABLE);

        // Test that the preferred nodes list is empty
        List<HostAddress> preferredNodes = arrowSplit.getPreferredNodes(null);
        assertNotNull(preferredNodes, "Preferred nodes list should not be null.");
        assertTrue(preferredNodes.isEmpty(), "Preferred nodes list should be empty.");
    }

    @Test
    public void testInfo()
    {
        ArrowSplit arrowSplit = createTestSplit(TEST_SCHEMA, TEST_TABLE);
        assertNotNull(arrowSplit.getInfo());

        Map<String, String> infoMap = arrowSplit.getInfoMap();
        assertNotNull(infoMap);
        assertTrue(infoMap.containsKey("schemaName"));
        assertEquals(infoMap.get("schemaName"), TEST_SCHEMA);
        assertTrue(infoMap.containsKey("tableName"));
        assertEquals(infoMap.get("tableName"), TEST_TABLE);

        // Test without a schema name
        arrowSplit = createTestSplit(null, TEST_TABLE);
        infoMap = arrowSplit.getInfoMap();
        assertNotNull(infoMap);
        assertTrue(infoMap.containsKey("tableName"));
        assertEquals(infoMap.get("tableName"), TEST_TABLE);
    }

    @Test
    public void testToString()
    {
        ArrowSplit arrowSplit = createTestSplit(TEST_SCHEMA, TEST_TABLE);
        assertNotNull(arrowSplit.getInfo());

        String arrowStr = arrowSplit.toString();
        assertTrue(arrowStr.contains(TEST_SCHEMA));
        assertTrue(arrowStr.contains(TEST_TABLE));

        // Test without a schema name
        arrowSplit = createTestSplit(null, TEST_TABLE);
        arrowStr = arrowSplit.toString();
        assertTrue(arrowStr.contains(TEST_TABLE));
    }

    FlightEndpoint createTestEndpoint()
    {
        byte[] ticketArray = new byte[] {1, 2, 3, 4};
        Ticket ticket = new Ticket(ByteBuffer.wrap(ticketArray).array());  // Wrap the byte array in a Ticket
        try {
            return new FlightEndpoint(ticket, new Location("http://localhost:8080"));
        }
        catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private ArrowSplit createTestSplit(String schemaName, String tableName)
    {
        return createTestSplit(schemaName, tableName, createTestEndpoint());
    }

    private ArrowSplit createTestSplit(String schemaName, String tableName, FlightEndpoint flightEndpoint)
    {
        return new ArrowSplit(schemaName, tableName, flightEndpoint.serialize().array());
    }
}
