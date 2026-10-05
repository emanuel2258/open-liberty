/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.webserver.plugin.utility.fat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.log.Log;

import componenttest.custom.junit.runner.FATRunner;
import componenttest.custom.junit.runner.Mode;
import componenttest.custom.junit.runner.Mode.TestMode;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.impl.LibertyServerFactory;

/**
 * Tests that the web server plugin config file (plugin-cfg.xml) is generated
 * the correct number of times across several deployment scenarios.
 *
 * Regression test for the issue where SRVE9103I was logged once per application
 * instead of once for the entire startup wave.
 *
 * The tests also cover the module-event contract: Liberty guarantees that
 * moduleStarting/moduleStarted are always bracketed by applicationStarting/
 * applicationStarted for the same application. The EAR test verifies that an
 * EAR containing multiple web modules is still counted as a single application
 * event pair and does not cause extra plugin generations.
 */
@RunWith(FATRunner.class)
public class PluginAutoGenerateTest {

    private static final Class<?> c = PluginAutoGenerateTest.class;

    /** Message ID logged each time the plugin config is automatically generated. */
    private static final String SRVE9103I = "SRVE9103I:";

    static LibertyServer multiAppServer = LibertyServerFactory.getLibertyServer("multiAppServer");

    @BeforeClass
    public static void setUp() throws Exception {
        // Applications are pre-staged as static files under
        // publish/servers/multiAppServer/apps/ and are copied into the server directory
        // by LibertyServerFactory.getLibertyServer() before startServer() is called.
        //
        // Three standalone WARs (testApp1-3) reproduce the original multiple-SRVE9103I
        // issue. The two-module EAR (multiModuleEar) verifies that module events
        // (moduleStarting/moduleStarted) do not introduce unbalanced updatesInProgress
        // tokens that would suppress or duplicate plugin generation.
        multiAppServer.startServer();

        // Wait for server ready message before asserting log counts
        assertNotNull("Server did not start: CWWKF0011I not found",
                      multiAppServer.waitForStringInLog("CWWKF0011I"));

        // Wait for all three standalone apps to be started
        for (int i = 1; i <= 3; i++) {
            assertNotNull("Application testApp" + i + " did not start (CWWKZ0001I not found)",
                          multiAppServer.waitForStringInLog("CWWKZ0001I.*testApp" + i));
        }

        // Wait for the EAR to be started
        assertNotNull("Application multiModuleEar did not start (CWWKZ0001I not found)",
                      multiAppServer.waitForStringInLog("CWWKZ0001I.*multiModuleEar"));

        // Wait for the plugin generation to actually occur. The debounce in
        // GeneratePluginConfigListener delays generation by ~500 ms after the last
        // applicationStarted event, so SRVE9103I may appear slightly after all
        // CWWKZ0001I messages. Waiting here ensures findStringsInLogs sees the entry.
        assertNotNull("Plugin config was never generated (SRVE9103I not found after startup)",
                      multiAppServer.waitForStringInLog(SRVE9103I));
    }

    @AfterClass
    public static void tearDown() throws Exception {
        try {
            if (multiAppServer.isStarted()) {
                multiAppServer.stopServer();
            }
        } catch (Exception e) {
            Log.info(c, "tearDown", "Failed to stop server: " + e);
        }
    }

    /**
     * Verifies that the web server plugin config file is generated exactly once
     * during server startup even when multiple applications and a multi-module EAR
     * are deployed.
     *
     * <p>Before the fix, each application's startup independently triggered a plugin
     * regeneration (N occurrences of SRVE9103I for N applications). After the fix,
     * only a single generation should occur regardless of the number of applications
     * or the number of web modules within an EAR.
     *
     * <p>A result of 0 would indicate the counter was permanently leaked (stuck above
     * zero) so generation never fired — typically caused by unbalanced set/run pairs.
     * A result &gt; 1 would indicate per-app (or per-module) triggering was not suppressed.
     */
    @Mode(TestMode.LITE)
    @Test
    public void testSinglePluginGenerationOnStartup() throws Exception {
        final String methodName = "testSinglePluginGenerationOnStartup";
        Log.entering(c, methodName);

        // Search from the very beginning of the log for the automatic plugin generation message.
        // The server was started in @BeforeClass so all SRVE9103I messages are already present.
        List<String> pluginGenerations = multiAppServer.findStringsInLogs(SRVE9103I);

        Log.info(c, methodName, "Found " + pluginGenerations.size() + " occurrence(s) of " + SRVE9103I);
        for (String line : pluginGenerations) {
            Log.info(c, methodName, "  " + line);
        }

        assertEquals("Plugin config should be generated exactly once during server startup with "
                     + "3 standalone WAR apps and 1 two-module EAR, "
                     + "but SRVE9103I was found " + pluginGenerations.size() + " time(s). "
                     + "0 occurrences means the updatesInProgress counter was permanently leaked "
                     + "(unbalanced set/run pair). >1 occurrence means per-app or per-module "
                     + "triggering was not correctly suppressed during the config-update wave.",
                     1, pluginGenerations.size());

        Log.exiting(c, methodName);
    }
}
