/*******************************************************************************
 * Copyright (c) 2017, 2020 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.jdbc.fat.loadfromapp;

import java.io.File;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.ShrinkHelper;

import componenttest.annotation.Server;
import componenttest.annotation.TestServlet;
import componenttest.annotation.TestServlets;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.utils.FATServletClient;
import web.derby.DerbyLoadFromAppServlet;
import web.other.LoadFromAppServlet;

/**
 * This test bucket will cover JDBC drivers being loaded from the application
 * instead of from libraries configured in server.xml.
 */
@RunWith(FATRunner.class)
public class JDBCLoadFromAppTest extends FATServletClient {

    @Server("com.ibm.ws.jdbc.fat.loadfromapp")
    @TestServlets(value = {
                            @TestServlet(servlet = DerbyLoadFromAppServlet.class, path = "derbyApp/DerbyLoadFromAppServlet"),
                            @TestServlet(servlet = LoadFromAppServlet.class, path = "otherApp/LoadFromAppServlet")
    })
    public static LibertyServer server;

    @BeforeClass
    public static void setUp() throws Exception {
        WebArchive derbyApp = ShrinkWrap.create(WebArchive.class, "derbyApp.war")
                        .addPackage("web.derby") //
                        .addAsWebInfResource(new File("test-applications/derbyapp/resources/WEB-INF/ibm-web-bnd.xml"))
                        .addAsLibrary(new File("publish/shared/resources/derby/derby.jar")) //
                        .addPackage("jdbc.driver.proxy"); // delegates to the Derby JDBC driver
        ShrinkHelper.exportAppToServer(server, derbyApp);

        WebArchive otherWAR = ShrinkWrap.create(WebArchive.class, "otherApp.war")
                        .addAsWebInfResource(new File("test-applications/otherapp/resources/WEB-INF/ibm-web-bnd.xml"))
                        .addPackage("web.other") //
                        .addPackage("jdbc.driver.mini") // barely usable, fake jdbc driver included in app
                        .addPackage("jdbc.driver.mini.jse") // java.sql.Driver implementation for the above
                        .addAsServiceProvider(java.sql.Driver.class, jdbc.driver.mini.jse.DriverImpl.class)
                        .addPackage("jdbc.driver.proxy"); // delegates to the "mini" JDBC driver

        JavaArchive ejb1JAR = ShrinkWrap.create(JavaArchive.class, "ejb1.jar")
                        .addPackage("ejb.first");

        JavaArchive ejb2JAR = ShrinkWrap.create(JavaArchive.class, "ejb2.jar")
                        .addPackage("ejb.second");

        JavaArchive lmJAR = ShrinkWrap.create(JavaArchive.class, "top-level-login-modules.jar")
                        .addPackage("loginmod");

        EnterpriseArchive otherApp = ShrinkWrap.create(EnterpriseArchive.class, "otherApp.ear")
                        .addAsModule(otherWAR)
                        .addAsModule(ejb1JAR)
                        .addAsModule(ejb2JAR)
                        .addAsLibrary(lmJAR);

        ShrinkHelper.exportAppToServer(server, otherApp);

        // TODO remove this once libraryRef is made optional
        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "ignoreThisUselessLibrary.jar");
        jar.addPackage("com.ibm.ws.jdbc.fat.loadfromapp"); // no login modules here
        ShrinkHelper.exportToServer(server, "/", jar);

        server.addInstalledAppForValidation("derbyApp");
        server.addInstalledAppForValidation("otherApp");

        server.startServer();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        server.stopServer("CWWKE0701E.*JAASLoginModuleConfigImpl", // TODO remove this if we enable loading login modules from web applications
                          "SRVE9967W.*derbyLocale" // ignore missing Derby locales
        );
    }
}
