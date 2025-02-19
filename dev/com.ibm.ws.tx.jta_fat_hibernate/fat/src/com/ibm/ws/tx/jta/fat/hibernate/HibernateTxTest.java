/*******************************************************************************
 * Copyright (c) 2018 IBM Corporation and others.
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
package com.ibm.ws.tx.jta.fat.hibernate;

import java.io.File;

import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.ShrinkHelper;

import componenttest.annotation.Server;
import componenttest.annotation.TestServlet;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.utils.FATServletClient;
import fat.tx.jta.hibernate.web.HibernateTxTestServlet;

@RunWith(FATRunner.class)
public class HibernateTxTest extends FATServletClient {

    public static final String APP_NAME = "hibernateApp";

    @Server("tx.jta_fat_hibernate")
    @TestServlet(servlet = HibernateTxTestServlet.class, contextRoot = APP_NAME)
    public static LibertyServer server;

    @BeforeClass
    public static void setUp() throws Exception {
        WebArchive app = ShrinkHelper.buildDefaultApp(APP_NAME, "fat.tx.jta.hibernate.web")
                        .addAsLibraries(new File("publish/files/hibernate/").listFiles());
        ShrinkHelper.exportAppToServer(server, app);
        server.addInstalledAppForValidation(APP_NAME);
        server.startServer();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        server.stopServer("SRVE9967W"); // Ignore manifest classpath errors from all the third-party libs we're pulling in
    }
}
