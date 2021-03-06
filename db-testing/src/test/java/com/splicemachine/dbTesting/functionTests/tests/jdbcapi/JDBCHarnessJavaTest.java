/*
 * Apache Derby is a subproject of the Apache DB project, and is licensed under
 * the Apache License, Version 2.0 (the "License"); you may not use these files
 * except in compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Splice Machine, Inc. has modified this file.
 *
 * All Splice Machine modifications are Copyright 2012 - 2016 Splice Machine, Inc.,
 * and are licensed to you under the License; you may not use this file except in
 * compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */
package com.splicemachine.dbTesting.functionTests.tests.jdbcapi;

import junit.framework.Test;
import junit.framework.TestSuite;

import com.splicemachine.dbTesting.functionTests.util.HarnessJavaTest;
import com.splicemachine.dbTesting.junit.TestConfiguration;

/**
 * Run a jdbcapi '.java' test from the old harness in the Junit infrastructure.
 * The test's output is compared to a master file using the facilities
 * of the super class CanonTestCase.
 * <BR>
 * This allows a faster switch to running all tests under a single
 * JUnit infrastructure. Running a test using this class does not
 * preclude it from being converted to a real JUnit assert based test.
 *
 */
public class JDBCHarnessJavaTest extends HarnessJavaTest {
    
    /**
     * Tests that run in both client and embedded.
     * Ideally both for a test of JDBC api functionality.
     */
    private static final String[] JDBCAPI_TESTS_BOTH =
    {
            // from old jdbc20.runall
            "connectionJdbc20",
            "resultsetJdbc20",           
            
            // from old jdbcapi.runall
            // "derbyStress",       TODO: Need a way to control heap size from Junit tests
            // "prepStmtMetaData",  TODO: convert - different canon for client
            "maxfieldsize",
            "SetQueryTimeoutTest",
            "rsgetXXXcolumnNames",
            
    };
    
    private JDBCHarnessJavaTest(String name) {
        super(name);
     }

    protected String getArea() {
        return "jdbcapi";
    }
    
    public static Test suite()
    {
        TestSuite suite = new TestSuite("jdbcapi: old harness java tests");
        suite.addTest(baseSuite("embedded", JDBCAPI_TESTS_BOTH));
        suite.addTest(TestConfiguration.clientServerDecorator(
                baseSuite("clientserver", JDBCAPI_TESTS_BOTH)));
        return suite;
    }
   
    private static Test baseSuite(String which, String[] set) {
        TestSuite suite = new TestSuite("jdbcapi: " + which);
        for (int i = 0; i < set.length; i++)
        {
            suite.addTest(decorate(new JDBCHarnessJavaTest(set[i])));
        }
        return suite;
    }
}
