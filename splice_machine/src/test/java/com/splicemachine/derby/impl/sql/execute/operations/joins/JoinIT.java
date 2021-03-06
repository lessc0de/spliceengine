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

package com.splicemachine.derby.impl.sql.execute.operations.joins;

import com.splicemachine.derby.test.framework.*;
import com.splicemachine.derby.test.framework.SpliceWatcher;
import com.splicemachine.homeless.TestUtils;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import com.splicemachine.test_tools.TableCreator;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;

/**
 * Integ test for Bug 887 and Bug 976.
 *
 * @author Jeff Cunningham
 *         Date: 11/19/13
 */
public class JoinIT extends SpliceUnitTest {
    public static final String CLASS_NAME = JoinIT.class.getSimpleName();

    protected static final SpliceWatcher spliceClassWatcher = new SpliceWatcher(CLASS_NAME);

    protected static final SpliceSchemaWatcher schemaWatcher = new SpliceSchemaWatcher(CLASS_NAME);

    protected static final SpliceTableWatcher A_TABLE = new SpliceTableWatcher("A",schemaWatcher.schemaName,
            "(a1 int not null primary key, a2 int, a3 int, a4 int, a5 int, a6 int)");
    protected static final SpliceTableWatcher B_TABLE = new SpliceTableWatcher("B",schemaWatcher.schemaName,
            "(b1 int not null primary key, b2 int, b3 int, b4 int, b5 int, b6 int)");
    protected static final SpliceTableWatcher C_TABLE = new SpliceTableWatcher("C",schemaWatcher.schemaName,
            "(c1 int not null, c2 int, c3 int not null, c4 int, c5 int, c6 int)");
    protected static final SpliceTableWatcher D_TABLE = new SpliceTableWatcher("D",schemaWatcher.schemaName,
            "(d1 int not null, d2 int, d3 int not null, d4 int, d5 int, d6 int)");
    protected static final SpliceTableWatcher B_PRIME_TABLE = new SpliceTableWatcher("B_PRIME",schemaWatcher.schemaName,
            "(b1 int, b2 int, b3 int, b4 int, b5 int, b6 int, c1 int not null, c2 int, c3 int not null, c4 int, c5 int, c6 int)");

    private static final String A_VALS =
            "INSERT INTO A VALUES (1,1,3,6,NULL,2),(2,3,2,4,2,2),(3,4,2,NULL,NULL,NULL),(4,NULL,4,2,5,2),(5,2,3,5,7,4),(7,1,4,2,3,4),(8,8,8,8,8,8),(6,7,3,2,3,4)";
    private static final String B_VALS =
            "INSERT INTO B VALUES (6,7,2,3,NULL,1),(4,5,9,6,3,2),(1,4,2,NULL,NULL,NULL),(5,NULL,2,2,5,2),(3,2,3,3,1,4),(7,3,3,3,3,3),(9,3,3,3,3,3)";
    private static final String C_VALS =
            "INSERT INTO C VALUES (3,7,7,3,NULL,1),(8,3,9,1,3,2),(1,4,1,NULL,NULL,NULL),(3,NULL,1,2,4,2),(2,2,5,3,2,4),(1,7,2,3,1,1),(3,8,4,2,4,6)";
    private static final String D_VALS =
            "INSERT INTO D VALUES (1,7,2,3,NULL,3),(2,3,9,1,1,2),(2,2,2,NULL,3,2),(1,NULL,3,2,2,1),(2,2,5,3,2,3),(2,5,6,3,7,2)";
    private static final String B_PRIME_VALS =
            "INSERT INTO B_PRIME VALUES (6,7,2,3,NULL,1,3,7,7,3,NULL,1),(4,5,9,6,3,2,8,3,9,1,3,2),(1,4,2,NULL,NULL,NULL,1,4,1,NULL,NULL,NULL),"+
                    "(5,NULL,2,2,5,2,3,NULL,1,2,4,2),(3,2,3,3,1,4,2,2,5,3,2,4),(7,3,3,3,3,3,1,7,2,3,1,1),(9,3,3,3,3,3,3,8,4,2,4,6)";

    @ClassRule
    public static TestRule chain = RuleChain.outerRule(spliceClassWatcher)
            .around(schemaWatcher)
            .around(A_TABLE)
            .around(B_TABLE)
            .around(C_TABLE)
            .around(D_TABLE)
            .around(B_PRIME_TABLE)
            .around(new SpliceDataWatcher() {
                @Override
                protected void starting(Description description) {
                    try {
                        spliceClassWatcher.getStatement().executeUpdate(A_VALS);
                        spliceClassWatcher.getStatement().executeUpdate(B_VALS);
                        spliceClassWatcher.getStatement().executeUpdate(C_VALS);
                        spliceClassWatcher.getStatement().executeUpdate(D_VALS);
                        spliceClassWatcher.getStatement().executeUpdate(B_PRIME_VALS);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        spliceClassWatcher.closeAll();
                    }
                }
            }).around(new SpliceDataWatcher(){
                @Override
                protected void starting(Description description){
                   try(CallableStatement cs = spliceClassWatcher.prepareCall("call SYSCS_UTIL.COLLECT_SCHEMA_STATISTICS(?,false)")){
                       cs.setString(1,schemaWatcher.schemaName);
                       cs.execute();
                   }catch(Exception e){
                       throw new RuntimeException(e);
                   }
                }
            });

    @Rule
    public SpliceWatcher methodWatcher = new SpliceWatcher();

    /**
     * Tests bug 887 - NPE and ConcurrentModificationException
     *
     * @throws Exception fail
     */
    @Test
    public void testBug887() throws Exception {
        String query = format("select a1,b1,c1,c3,d1,d3 from %s.D join (%s.A left outer join (%s.B join %s.C on b2=c2) on a1=b1) on d3=b3 and d1=a2",
                CLASS_NAME, CLASS_NAME, CLASS_NAME, CLASS_NAME);

        ResultSet rs = methodWatcher.executeQuery(query);
        int nRows = resultSetSize(rs);
        Assert.assertEquals("Expecting 2 rows from join.", 2, nRows);
    }

    /**
     * Tests bug 887 - NPE and ConcurrentModificationException
     *
     * @throws Exception fail
     */
    @Test
    public void testJoinBug887() throws Exception {
        String query = format("select * from %s.B join %s.C on b2=c2",
                CLASS_NAME, CLASS_NAME, CLASS_NAME, CLASS_NAME);

        ResultSet rs = methodWatcher.executeQuery(query);
//        int nRows = TestUtils.printResult(query, rs, System.out);
        int nRows = resultSetSize(rs);
        Assert.assertEquals("Expecting 6 rows from join.", 6, nRows);
    }

    /**
     * Bug 976 (the original bug)
     * Getting one less row from splice.
     *
     * @throws Exception fail
     */
    @Test
    public void testAllJoinBug976Compare() throws Exception {

        String query = format("select a1,b1,c1,c3,d1,d3 from %s.D join (%s.A left outer join (%s.B join %s.C on b2=c2) on a1=b1) on d3=b3 and d1=a2",
                CLASS_NAME, CLASS_NAME, CLASS_NAME, CLASS_NAME);

        String expectedColumns = "A1 B1 C1 C3 D1 D3";
        List<String> expectedRows = Arrays.asList(
                "7 7 8 9 1 3",
                "1 1 1 1 1 2");
        ResultSet rs = methodWatcher.executeQuery(query);
        TestUtils.FormattedResult expected = TestUtils.FormattedResult.ResultFactory.convert(query, expectedColumns, expectedRows, "\\s+");
        TestUtils.FormattedResult actual = TestUtils.FormattedResult.ResultFactory.convert(query, rs);
        Assert.assertEquals("Actual results didn't match expected.", expected.toString(), actual.toString());
    }

    /**
     * Bug 976
     * Getting different results from splice for this Left Outer nested join.
     *
     * @throws Exception fail
     */
    @Test
    public void testLeftOuterJoinBug976Compare() throws Exception {
        String query = "select * from JoinIT.A left outer join (JoinIT.B join JoinIT.C on b2=c2) on a1=b1";

        String expectedColumns = "A1 A2 A3 A4 A5 A6 B1 B2 B3 B4 B5 B6 C1 C2 C3 C4 C5 C6";
        List<String> expectedRows = Arrays.asList(
                "3 4 2 (null) (null) (null) 3 2 3 3 1 4 2 2 5 3 2 4",
                "7 1 4 2 3 4 7 3 3 3 3 3 8 3 9 1 3 2",
                "1 1 3 6 (null) 2 1 4 2 (null) (null) (null) 1 4 1 (null) (null) (null)",
                "6 7 3 2 3 4 6 7 2 3 (null) 1 3 7 7 3 (null) 1",
                "6 7 3 2 3 4 6 7 2 3 (null) 1 1 7 2 3 1 1",
                "2 3 2 4 2 2 (null) (null) (null) (null) (null) (null) (null) (null) (null) (null) (null) (null)",
                "5 2 3 5 7 4 (null) (null) (null) (null) (null) (null) (null) (null) (null) (null) (null) (null)",
                "8 8 8 8 8 8 (null) (null) (null) (null) (null) (null) (null) (null) (null) (null) (null) (null)",
                "4 (null) 4 2 5 2 (null) (null) (null) (null) (null) (null) (null) (null) (null) (null) (null) (null)");
        ResultSet rs = methodWatcher.executeQuery(query);
        TestUtils.FormattedResult expected = TestUtils.FormattedResult.ResultFactory.convert(query, expectedColumns, expectedRows, "\\s+");
        TestUtils.FormattedResult actual = TestUtils.FormattedResult.ResultFactory.convert(query, rs);
        Assert.assertEquals("Actual results didn't match expected.", expected.toString(), actual.toString());
    }

    /**
     * Bug 976
     * We get expected results from this (non-outer) join.
     *
     * @throws Exception fail
     */
    @Test
    public void testJoinBug976Compare() throws Exception {

        String query = format("select * from %s.A join (%s.B join %s.C on b2=c2) on a1=b1",
                CLASS_NAME, CLASS_NAME, CLASS_NAME, CLASS_NAME);

        String expectedColumns = "A1 A2 A3 A4 A5 A6 B1 B2 B3 B4 B5 B6 C1 C2 C3 C4 C5 C6";
        List<String> expectedRows = Arrays.asList(
                "3 4 2 (null) (null) (null) 3 2 3 3 1 4 2 2 5 3 2 4",
                "7 1 4 2 3 4 7 3 3 3 3 3 8 3 9 1 3 2",
                "1 1 3 6 (null) 2 1 4 2 (null) (null) (null) 1 4 1 (null) (null) (null)",
                "6 7 3 2 3 4 6 7 2 3 (null) 1 3 7 7 3 (null) 1",
                "6 7 3 2 3 4 6 7 2 3 (null) 1 1 7 2 3 1 1");
        ResultSet rs = methodWatcher.executeQuery(query);
        TestUtils.FormattedResult expected = TestUtils.FormattedResult.ResultFactory.convert(query, expectedColumns, expectedRows, "\\s+");
        TestUtils.FormattedResult actual = TestUtils.FormattedResult.ResultFactory.convert(query, rs);
        Assert.assertEquals("Actual results didn't match expected.", expected.toString(), actual.toString());
    }

    /**
     * Bug 976
     * Nested join returns expected rows.
     *
     * @throws Exception fail
     */
    @Test
    public void testSmallJoinBug976Compare() throws Exception {

        String query = format("select * from %s.B join %s.C on b2=c2",
                CLASS_NAME, CLASS_NAME, CLASS_NAME, CLASS_NAME);

        String expectedColumns = "B1 B2 B3 B4 B5 B6 C1 C2 C3 C4 C5 C6";
        List<String> expectedRows = Arrays.asList(
                "3 2 3 3 1 4 2 2 5 3 2 4",
                "9 3 3 3 3 3 8 3 9 1 3 2",
                "7 3 3 3 3 3 8 3 9 1 3 2",
                "1 4 2 (null) (null) (null) 1 4 1 (null) (null) (null)",
                "6 7 2 3 (null) 1 3 7 7 3 (null) 1",
                "6 7 2 3 (null) 1 1 7 2 3 1 1");
        ResultSet rs = methodWatcher.executeQuery(query);
        TestUtils.FormattedResult expected = TestUtils.FormattedResult.ResultFactory.convert(query, expectedColumns, expectedRows, "\\s+");
        TestUtils.FormattedResult actual = TestUtils.FormattedResult.ResultFactory.convert(query, rs);
        Assert.assertEquals("Actual results didn't match expected.", expected.toString(), actual.toString());
    }

    /**
     * Bug 976
     * Check to see that the outer join knows what it's doing by setting up the right side
     * with a table instead of a join.
     *
     * @throws Exception fail
     */
    @Test
    public void testLeftOuterJoinWithTable() throws Exception {
        String query = format("select * from %s.A left outer join %s.B_PRIME on a1=b1",
                CLASS_NAME, CLASS_NAME);

        String expectedColumns = "A1 A2 A3 A4 A5 A6 B1 B2 B3 B4 B5 B6 C1 C2 C3 C4 C5 C6";
        List<String> expectedRows = Arrays.asList(
                "6 7 3 2 3 4 6 7 2 3 (null) 1 3 7 7 3 (null) 1",
                "4 (null) 4 2 5 2 4 5 9 6 3 2 8 3 9 1 3 2",
                "1 1 3 6 (null) 2 1 4 2 (null) (null) (null) 1 4 1 (null) (null) (null)",
                "5 2 3 5 7 4 5 (null) 2 2 5 2 3 (null) 1 2 4 2",
                "3 4 2 (null) (null) (null) 3 2 3 3 1 4 2 2 5 3 2 4",
                "7 1 4 2 3 4 7 3 3 3 3 3 1 7 2 3 1 1",
                "8 8 8 8 8 8 (null) (null) (null) (null) (null) (null) (null) (null) (null) (null) (null) (null)",
                "2 3 2 4 2 2 (null) (null) (null) (null) (null) (null) (null) (null) (null) (null) (null) (null)");
        ResultSet rs = methodWatcher.executeQuery(query);
        TestUtils.FormattedResult expected = TestUtils.FormattedResult.ResultFactory.convert(query, expectedColumns, expectedRows, "\\s+");
        TestUtils.FormattedResult actual = TestUtils.FormattedResult.ResultFactory.convert(query, rs);
        Assert.assertEquals("Actual results didn't match expected.", expected.toString(), actual.toString());
    }

    /* DB-1672: Prior to fix BroadcastJoinStrategy would return not feasible if table was created in the last 1/2 sec or so. */
    @Test
    public void testBroadcastJoinJustAfterTableCreation() throws Exception {

        for (int i = 0; i < 6; i = i + 2) {

            String tableName1 = String.valueOf(RandomStringUtils.randomAlphabetic(9));
            String tableName2 = String.valueOf(RandomStringUtils.randomAlphabetic(9));

            TestConnection connection=methodWatcher.getOrCreateConnection();
            new TableCreator(connection).withCreate("create table %s (c1 int, c2 int, c3 int)")
                    .withTableName(schemaWatcher.schemaName+"."+tableName1).create();

            new TableCreator(connection).withCreate("create table %s (c1 int, c2 int, c3 int)")
                    .withTableName(schemaWatcher.schemaName+"."+tableName2).create();
            connection.collectStats(schemaWatcher.schemaName,tableName1);
            connection.collectStats(schemaWatcher.schemaName,tableName2);

            String sql = "select * from %1$s.%2$s a --SPLICE-PROPERTIES joinStrategy=BROADCAST \n join %1$s.%2$s b on a.c1=b.c2 ";

            ResultSet rs = methodWatcher.prepareStatement(String.format(sql,schemaWatcher.schemaName, tableName1, tableName2)).executeQuery();

            Assert.assertFalse(rs.next());
        }

    }


}
