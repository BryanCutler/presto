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
package com.facebook.presto.flightshim;

import com.facebook.airlift.resolver.ArtifactResolver;
import com.facebook.plugin.arrow.ArrowPlugin;
import com.facebook.plugin.arrow.BlockArrowWriterBuilder;
import com.facebook.plugin.arrow.ConnectorArrowSourceAdapter;
import com.facebook.presto.server.PluginManagerUtil;
import com.facebook.presto.server.PluginManagerUtil.PluginClassLoaderHandle;
import com.facebook.presto.spi.Plugin;
import com.google.common.collect.ImmutableList;
import com.google.common.io.MoreFiles;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static com.facebook.presto.flightshim.FlightShimPluginManager.FLIGHT_SHIM_SPI_PACKAGES;
import static com.facebook.presto.server.PluginManagerUtil.SPI_PACKAGES;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static org.testng.Assert.assertSame;

/**
 * Builds real plugin class loaders over an Arrow-based plugin to check which classes the shim and the
 * Presto server share with plugins.
 */
public class TestFlightShimPluginClassLoading
{
    private Path pluginsDir;

    @BeforeClass
    public void setUp()
            throws Exception
    {
        // One plugin directory holding the Arrow Flight connector, presto-common-arrow and arrow-vector
        pluginsDir = Files.createTempDirectory("flight-shim-plugins");
        Path pluginDir = Files.createDirectory(pluginsDir.resolve("arrow"));
        List<Class<?>> markers = ImmutableList.of(ArrowPlugin.class, BlockArrowWriterBuilder.class, VectorSchemaRoot.class);
        for (int i = 0; i < markers.size(); i++) {
            Files.createSymbolicLink(pluginDir.resolve("entry-" + i), codeSourceOf(markers.get(i)));
        }
    }

    @AfterClass(alwaysRun = true)
    public void tearDown()
            throws IOException
    {
        MoreFiles.deleteRecursively(pluginsDir, ALLOW_INSECURE);
    }

    @Test
    public void testShimSharesOnlyArrowAndAdapter()
            throws Exception
    {
        try (URLClassLoader pluginClassLoader = buildPluginClassLoader(FLIGHT_SHIM_SPI_PACKAGES)) {
            // Other classes in com.facebook.plugin.arrow belong to the plugin
            assertSame(pluginClassLoader.loadClass(ArrowPlugin.class.getName()).getClassLoader(), pluginClassLoader);
            assertSame(pluginClassLoader.loadClass(BlockArrowWriterBuilder.class.getName()).getClassLoader(), pluginClassLoader);

            // Classes that cross the ConnectorArrowSource boundary resolve to the shim's copies
            assertSame(pluginClassLoader.loadClass(ConnectorArrowSourceAdapter.class.getName()), ConnectorArrowSourceAdapter.class);
            assertSame(pluginClassLoader.loadClass(ConnectorArrowSourceAdapter.VectorSchemaRootHolderImpl.class.getName()), ConnectorArrowSourceAdapter.VectorSchemaRootHolderImpl.class);
            assertSame(pluginClassLoader.loadClass(VectorSchemaRoot.class.getName()), VectorSchemaRoot.class);
        }
    }

    @Test
    public void testServerDoesNotShareArrow()
            throws Exception
    {
        try (URLClassLoader pluginClassLoader = buildPluginClassLoader(SPI_PACKAGES)) {
            assertSame(pluginClassLoader.loadClass(ArrowPlugin.class.getName()).getClassLoader(), pluginClassLoader);
            assertSame(pluginClassLoader.loadClass(ConnectorArrowSourceAdapter.class.getName()).getClassLoader(), pluginClassLoader);
            assertSame(pluginClassLoader.loadClass(VectorSchemaRoot.class.getName()).getClassLoader(), pluginClassLoader);
        }
    }

    private URLClassLoader buildPluginClassLoader(List<String> spiPackages)
            throws Exception
    {
        List<PluginClassLoaderHandle> handles = PluginManagerUtil.buildClassLoaders(
                pluginsDir.toFile(),
                ImmutableList.of(),
                (ArtifactResolver) null,
                spiPackages,
                null,
                "META-INF/services/" + Plugin.class.getName(),
                getClass().getClassLoader());
        return handles.stream().collect(onlyElement()).getClassLoader();
    }

    private static Path codeSourceOf(Class<?> clazz)
            throws URISyntaxException
    {
        return Paths.get(clazz.getProtectionDomain().getCodeSource().getLocation().toURI());
    }
}
