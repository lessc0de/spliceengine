/*
 * Copyright 2012 - 2016 Splice Machine, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.splicemachine.derby.security;

import com.splicemachine.derby.test.framework.SpliceNetConnection;
import com.splicemachine.derby.test.framework.SpliceUserWatcher;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.util.Properties;

import static org.junit.Assert.fail;

public class AuthenticationIT {

    private static final String AUTH_IT_USER = "auth_it_user";
    private static final String AUTH_IT_PASS = "test_password";

    @Rule
    public SpliceUserWatcher spliceUserWatcher1 = new SpliceUserWatcher(AUTH_IT_USER, AUTH_IT_PASS);

    @Test
    public void valid() throws SQLException {
        SpliceNetConnection.getConnectionAs(AUTH_IT_USER, AUTH_IT_PASS);
    }

    @Test
    public void validUsernameIsNotCaseSensitive() throws SQLException {
        SpliceNetConnection.getConnectionAs(AUTH_IT_USER.toUpperCase(), AUTH_IT_PASS);
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // bad password
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    @Test(expected = SQLNonTransientConnectionException.class)
    public void badPassword() throws SQLException {
        SpliceNetConnection.getConnectionAs(AUTH_IT_USER, "bad_password");
    }

    @Test(expected = SQLNonTransientConnectionException.class)
    public void badPasswordExtraCharAtStart() throws SQLException {
        SpliceNetConnection.getConnectionAs(AUTH_IT_USER, "a" + AUTH_IT_PASS);
    }

    @Test(expected = SQLNonTransientConnectionException.class)
    public void badPasswordExtraCharAtEnd() throws SQLException {
        SpliceNetConnection.getConnectionAs(AUTH_IT_USER, AUTH_IT_PASS + "a");
    }

    @Test(expected = SQLNonTransientConnectionException.class)
    public void badPasswordCase() throws SQLException {
        SpliceNetConnection.getConnectionAs(AUTH_IT_USER, AUTH_IT_PASS.toUpperCase());
    }

    @Test(expected = SQLNonTransientConnectionException.class)
    public void badPasswordZeroLength() throws SQLException {
        SpliceNetConnection.getConnectionAs(AUTH_IT_USER, "");
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // bad username
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    @Test(expected = SQLNonTransientConnectionException.class)
    public void badUsername() throws SQLException {
        SpliceNetConnection.getConnectionAs("bad_username", AUTH_IT_PASS);
    }


    //DB-4618
    @Test
    public void invalidDbname() throws SQLException {
        String url = "jdbc:splice://localhost:1527/anotherdb;user=user;password=passwd";
        try {
            DriverManager.getConnection(url, new Properties());
            fail("Expected authentication failure");
        } catch (SQLNonTransientConnectionException e) {
            Assert.assertTrue(e.getSQLState().compareTo("08004") == 0);
        }
    }
}
