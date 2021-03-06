/*******************************************************************************
 * Copyright (c) 2017,2020 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package web.derby;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import javax.annotation.Resource;
import javax.naming.InitialContext;
import javax.security.auth.login.LoginException;
import javax.servlet.annotation.WebServlet;
import javax.sql.DataSource;

import org.junit.Test;

import componenttest.annotation.AllowedFFDC;
import componenttest.app.FATDatabaseServlet;

@SuppressWarnings("serial")
@WebServlet(urlPatterns = "/DerbyLoadFromAppServlet")
public class DerbyLoadFromAppServlet extends FATDatabaseServlet {
    @Resource
    private DataSource defaultDataSource;

    @Resource(name = "java:module/env/jdbc/sldsLoginModuleFromOtherApp", lookup = "jdbc/sharedLibDataSource")
    private DataSource sldsLoginModuleFromOtherApp;

    @Resource(name = "java:module/env/jdbc/sldsLoginModuleFromWebApp", lookup = "jdbc/sharedLibDataSource")
    private DataSource sldsLoginModuleFromWebApp;

    // Use a data source with generic properties element where the dataSource's jdbcDriver
    // specifies a data source class name, but is configured without any library,
    // in which case JDBC driver classes are loaded from the application's thread context class loader.
    @Test
    public void testDefaultDataSource() throws Exception {
        Connection con = defaultDataSource.getConnection("DerbyLoadFromAppServlet", "pwd1");
        try {
            assertEquals("Apache Derby", con.getMetaData().getDatabaseProductName());
        } finally {
            con.close();
        }
    }

    // Use a data source with derby properties element where the dataSource is configured
    // without any library, in which case the JDBC driver class name is inferred from the fact
    // that the derby properties element is used, and JDBC driver classes are loaded from the
    // application's thread context class loader.
    @Test
    public void testDerbyDataSource() throws Exception {
        DataSource ds = InitialContext.doLookup("jdbc/derby");
        Connection con = ds.getConnection();
        try {
            assertEquals("Apache Derby", con.getMetaData().getDatabaseProductName());
        } finally {
            con.close();
        }
    }

    // This basic test verifies that the application can at least load classes from the Derby library that it includes
    @Test
    public void testLoadDerbyClass() throws Exception {
        Class.forName("org.apache.derby.jdbc.EmbeddedDataSource");
    }

    /**
     * testLoginModuleFromOtherApp - verify that a login module that is packaged within another application can be used to authenticate
     * a data source that is defined in the current web application, where its resource reference specifies to use that login module.
     */
    @Test
    public void testLoginModuleFromOtherApp() throws Exception {
        try (Connection con = sldsLoginModuleFromOtherApp.getConnection()) {
            DatabaseMetaData metadata = con.getMetaData();
            assertEquals("appuser", metadata.getUserName());
        }
    }

    /**
     * testLoginModuleFromWebApp - verify that a login module that is packaged within the web application CANNOT be used to authenticate
     * to a data source when its resource reference specifies to use that login module.
     */
    @AllowedFFDC({ "javax.security.auth.login.LoginException", "javax.resource.ResourceException" }) // TODO remove if we decide to enable this scenario
    @Test
    public void testLoginModuleFromWebApp() throws Exception {
        try (Connection con = sldsLoginModuleFromWebApp.getConnection()) {
            DatabaseMetaData metadata = con.getMetaData();
            fail("authenticated as user " + metadata.getUserName());
            // TODO if we decide to enable this path, then switch to the following assert and remove the catch block
            // assertEquals("webappuser", metadata.getUserName());
        } catch (SQLException x) {
            Throwable cause = x;
            while (cause != null && !(cause instanceof LoginException))
                cause = cause.getCause();
            if (!(cause instanceof LoginException))
                throw x;
        }
    }

    // Obtain a connection with a user/password that is unique to this method and a corresponding method
    // in LoadFromAppServlet, and verify that the underlying JDBC driver loaded is the Derby Embedded JDBC driver
    // which is found in this application and not the fake "Mini" JDBC driver from the other application.
    @Test
    public void testMatchingByAppLoader() throws Exception {
        Connection con = defaultDataSource.getConnection("testMatchingByAppLoader", "pwd1");
        try {
            DatabaseMetaData metadata = con.getMetaData();
            assertEquals("Apache Derby Embedded JDBC Driver", metadata.getDriverName());
            assertEquals("testMatchingByAppLoader", metadata.getUserName());
            assertEquals(Connection.TRANSACTION_READ_COMMITTED, con.getTransactionIsolation()); // isn't REPEATABLE_READ because ProxyDataSource with generic properties is used
            assertEquals(null, con.getCatalog());
            assertFalse(con.isReadOnly());
        } finally {
            con.close();
        }
    }
}
