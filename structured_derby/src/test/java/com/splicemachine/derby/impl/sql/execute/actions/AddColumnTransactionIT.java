package com.splicemachine.derby.impl.sql.execute.actions;

import com.splicemachine.derby.test.framework.SpliceSchemaWatcher;
import com.splicemachine.derby.test.framework.SpliceTableWatcher;
import com.splicemachine.derby.test.framework.SpliceWatcher;
import com.splicemachine.derby.test.framework.TestConnection;
import com.splicemachine.derby.utils.ErrorState;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Scott Fines
 *         Date: 9/3/14
 */
public class AddColumnTransactionIT {
    public static final SpliceSchemaWatcher schemaWatcher = new SpliceSchemaWatcher(AddColumnTransactionIT.class.getSimpleName().toUpperCase());

    public static final SpliceTableWatcher table = new SpliceTableWatcher("A",schemaWatcher.schemaName,"(a int, b int)");
    public static final SpliceTableWatcher commitTable = new SpliceTableWatcher("B",schemaWatcher.schemaName,"(a int, b int)");
    public static final SpliceTableWatcher beforeTable = new SpliceTableWatcher("C",schemaWatcher.schemaName,"(a int, b int)");
    public static final SpliceTableWatcher afterTable = new SpliceTableWatcher("D",schemaWatcher.schemaName,"(a int, b int)");

    public static final SpliceWatcher classWatcher = new SpliceWatcher();

    public static final String query = "select * from " + table+" where a = ";
    @ClassRule
    public static TestRule chain = RuleChain.outerRule(classWatcher)
            .around(schemaWatcher)
            .around(table)
            .around(commitTable)
            .around(beforeTable)
            .around(afterTable);

    private static TestConnection conn1;
    private static TestConnection conn2;

    private long conn1Txn;
    private long conn2Txn;

    @BeforeClass
    public static void setUpClass() throws Exception {
        conn1 = classWatcher.getOrCreateConnection();
        conn2 = classWatcher.createConnection();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        conn1.close();
        conn2.close();
    }

    @After
    public void tearDown() throws Exception {
        conn1.rollback();
        conn1.reset();
        conn2.rollback();
        conn2.reset();
    }

    @Before
    public void setUp() throws Exception {
        conn1.setAutoCommit(false);
        conn2.setAutoCommit(false);
        conn1Txn = conn1.getCurrentTransactionId();
        conn2Txn = conn2.getCurrentTransactionId();
    }

    @Test
    public void testAddColumnWorksWithOneConnectionAndCommitDefaultNull() throws Exception {
        int aInt = 1;
        int bInt = 1;
        PreparedStatement preparedStatement = conn1.prepareStatement("insert into " + commitTable + " (a,b) values (?,?)");
        preparedStatement.setInt(1,aInt);
        preparedStatement.setInt(2,bInt);
        conn1.commit();

        conn1.createStatement().execute("alter table "+ commitTable+" add column c int");
        conn1.commit();

        ResultSet rs = conn1.query("select * from " + commitTable);

        while(rs.next()){
            rs.getInt("C");
            Assert.assertTrue("Column C is not null!",rs.wasNull());
        }
    }

    @Test
    public void testAddColumnWorksWithOneConnectionAndCommitDefaultValue() throws Exception {
        int aInt = 2;
        int bInt = 2;
        PreparedStatement preparedStatement = conn1.prepareStatement("insert into " + commitTable + " (a,b) values (?,?)");
        preparedStatement.setInt(1,aInt);
        preparedStatement.setInt(2,bInt);
        conn1.commit();

        conn1.createStatement().execute("alter table "+ commitTable+" add column d int with default 2");
        conn1.commit();

        ResultSet rs = conn1.query("select * from " + commitTable +" where a = "+aInt);
        Assert.assertEquals("Incorrect metadata reporting!",3,rs.getMetaData().getColumnCount());

        while(rs.next()){
            int anInt = rs.getInt("D");
            Assert.assertEquals("Incorrect value for column D",2,anInt);
            Assert.assertTrue("Column D is null!",!rs.wasNull());
        }
    }

    @Test
    public void testAddColumnWorksWithOneTransaction() throws Exception {
        int aInt = 3;
        int bInt = 3;
        PreparedStatement preparedStatement = conn1.prepareStatement("insert into " + table + " (a,b) values (?,?)");
        preparedStatement.setInt(1,aInt);
        preparedStatement.setInt(2, bInt);

        conn1.createStatement().execute("alter table " + commitTable + " add column e int with default 2");

        ResultSet rs = conn1.query("select * from " + commitTable +" where a = "+aInt);

        while(rs.next()){
            int anInt = rs.getInt("E");
            Assert.assertEquals("Incorrect value for column E",2,anInt);
            Assert.assertTrue("Column E is null!",!rs.wasNull());
        }
    }

    @Test
    public void testAddColumnRemovedWhenRolledBack() throws Exception {
        int aInt = 4;
        testAddColumnWorksWithOneTransaction();

        conn1.rollback();

        ResultSet rs = conn1.query("select * from " + commitTable+ " where a = "+ aInt);

        if(rs.next()){
            try{
                int anInt = rs.getInt("E");
                Assert.fail("did not fail!");
            }catch(SQLException se){
                Assert.assertEquals("Incorrect error message!", ErrorState.COLUMN_NOT_FOUND.getSqlState(),se.getSQLState());
            }
        }
    }

    @Test
    public void testAddColumnIgnoredByOtherTransaction() throws Exception {
        int aInt = 4;
        int bInt = 4;
        PreparedStatement preparedStatement = conn1.prepareStatement("insert into " + table + " (a,b) values (?,?)");
        preparedStatement.setInt(1,aInt);
        preparedStatement.setInt(2,bInt);

        conn1.createStatement().execute("alter table "+ commitTable+" add column f int with default 2");

        ResultSet rs = conn1.query("select * from " + commitTable +" where a = "+aInt);

        while(rs.next()){
            int anInt = rs.getInt("F");
            Assert.assertEquals("Incorrect value for column f",2,anInt);
            Assert.assertTrue("Column f is null!",!rs.wasNull());
        }

        conn2.query("select * from " + commitTable+ " where a = "+ aInt);

        if(rs.next()){
            try{
                int anInt = rs.getInt("F");
                Assert.fail("did not fail!");
            }catch(SQLException se){
                Assert.assertEquals("Incorrect error message!", ErrorState.COLUMN_NOT_FOUND.getSqlState(),se.getSQLState());
            }
        }

    }
}