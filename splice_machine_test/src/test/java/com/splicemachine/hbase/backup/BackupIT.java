package com.splicemachine.hbase.backup;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.splicemachine.derby.test.framework.*;
import com.splicemachine.test.*;
import com.splicemachine.utils.SpliceUtilities;
import org.junit.*;
import org.junit.experimental.categories.Category;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.junit.runner.Description;

import static java.lang.String.format;
@Category(value = {SerialTest.class, SlowTest.class})
public class BackupIT extends SpliceUnitTest {
    protected static SpliceWatcher spliceClassWatcher = new SpliceWatcher();
    public static final String CLASS_NAME = BackupIT.class.getSimpleName().toUpperCase();
    protected static String TABLE_NAME = "A";
    private static final String SCHEMA_NAME = BackupIT.class.getSimpleName().toUpperCase();
    protected static File backupDir;

    protected static SpliceSchemaWatcher spliceSchemaWatcher = new SpliceSchemaWatcher(CLASS_NAME);
    protected static SpliceTableWatcher spliceTableWatcher =
            new SpliceTableWatcher(TABLE_NAME, SCHEMA_NAME, "(I INT, D DOUBLE)");

    protected Connection connection;

    @ClassRule
    public static TestRule chain = RuleChain.outerRule(spliceClassWatcher)
            .around(spliceSchemaWatcher)
            .around(spliceTableWatcher).around(new SpliceDataWatcher() {
                @Override
                protected void starting(Description description) {
                    PreparedStatement ps;
                    try {
                        ps = spliceClassWatcher.prepareStatement(format("insert into %s.%s (i, d) values (?, ?)", SCHEMA_NAME, TABLE_NAME));
                        for (int j = 0; j < 100; ++j) {
                            for (int i = 0; i < 10; i++) {
                                ps.setInt(1, i);
                                ps.setDouble(2, i * 1.0);
                                ps.execute();
                            }
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
    @Rule
    public SpliceWatcher methodWatcher = new SpliceWatcher();

    @Before
    public void setup() throws Exception
    {
        connection = methodWatcher.createConnection();
        String tmpDir = System.getProperty("java.io.tmpdir");
        backupDir = new File(tmpDir, "backup");
        backupDir.mkdirs();
        System.out.println(backupDir.getAbsolutePath());
    }

    @Test
    public void testBackup() throws Exception{

        backup("full");
        long backupId1 = getBackupId();
        int backupItems1 = getBackupItems(backupId1);

        insertData();
        backup("incremental");

        // verify incremental backup did not backup all tables
        long backupId2 = getBackupId();
        int backupItems2 = getBackupItems(backupId2);
        Assert.assertTrue(backupItems1 > backupItems2);

        // Verify incremental backup include changes for table 'A'
        long conglomerateNumber = getConglomerateNumber(spliceSchemaWatcher.schemaName, TABLE_NAME);
        verifyIncrementalBackup(backupId2, conglomerateNumber, true);

        // Compact table 'A', and verify it's not in next incremental backup
        HBaseAdmin admin = SpliceUtilities.getAdmin();
        admin.flush((new Long(conglomerateNumber)).toString());
        admin.majorCompact((new Long(conglomerateNumber)).toString());
        Thread.sleep(10000);

        //Split table 'A', and verify it is not in next incremental backup
        spliceClassWatcher.splitTable(TABLE_NAME, SCHEMA_NAME, 250);
        spliceClassWatcher.splitTable(TABLE_NAME, SCHEMA_NAME, 500);
        spliceClassWatcher.splitTable(TABLE_NAME, SCHEMA_NAME, 750);
        Thread.sleep(10000);
        backup("incremental");
        verifyIncrementalBackup(getBackupId(), conglomerateNumber, false);
    }

    private void insertData() throws Exception {
        PreparedStatement ps;
        try {
            ps = connection.prepareStatement(format("insert into %s.%s (i, d) values (?, ?)", SCHEMA_NAME, TABLE_NAME));
            for (int j = 0; j < 100; ++j) {
                for (int i = 0; i < 10; i++) {
                    ps.setInt(1, i);
                    ps.setDouble(2, i * 1.0);
                    ps.execute();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void backup(String type) throws Exception
    {
        System.out.println("Start " + type + " backup ...");
        PreparedStatement ps = connection.prepareStatement(
                format("call SYSCS_UTIL.SYSCS_BACKUP_DATABASE('%s', '%s')", backupDir.getAbsolutePath(), type));
        ps.execute();
        System.out.println("Backup completed.");

    }

    private int count() throws Exception{
        PreparedStatement ps = connection.prepareStatement(
                format("select * from %s.%s", spliceSchemaWatcher.schemaName,TABLE_NAME));
        ResultSet rs = ps.executeQuery();
        int count = 0;
        while(rs.next()) {
            count++;
        }
        return count;
    }

    private long getBackupId() throws Exception {
        PreparedStatement ps = connection.prepareStatement(
                "select max(backup_id) from sys.sysbackup");
        ResultSet rs = ps.executeQuery();
        Assert.assertTrue(rs.next());
        long backupId = rs.getLong(1);
        return backupId;
    }

    private int getBackupItems(long backupId) throws Exception{
        PreparedStatement ps = connection.prepareStatement(
                "select count(*) from sys.sysbackupitems where backup_id=?");
        ps.setLong(1, backupId);
        ResultSet rs = ps.executeQuery();
        Assert.assertTrue(rs.next());
        int count = rs.getInt(1);
        return count;
    }

    private long getConglomerateNumber(String schemaName, String tableName) throws Exception {

        PreparedStatement ps = connection.prepareStatement(
                "select conglomeratenumber from sys.systables t, sys.sysconglomerates c, sys.sysschemas s where c.tableid=t.tableid and t.tablename=? and s.schemaname=? and s.schemaid=c.schemaid");
        ps.setString(1, tableName);
        ps.setString(2, schemaName);
        ResultSet rs = ps.executeQuery();
        Assert.assertTrue(rs.next());
        long congId = rs.getLong(1);
        return congId;

    }

    private void verifyIncrementalBackup(long backupId, long backupItem, boolean expectedValue) throws Exception {

        PreparedStatement ps = connection.prepareStatement(
                "select * from sys.sysbackupitems where backup_id=? and item=?");
        ps.setLong(1, backupId);
        ps.setString(2, (new Long(backupItem)).toString());
        ResultSet rs = ps.executeQuery();
        Assert.assertTrue(rs.next() == expectedValue);
    }

    private void restore(long backupId) throws Exception
    {
        System.out.println("Start restore ...");
        PreparedStatement ps = connection.prepareStatement(format("call SYSCS_UTIL.SYSCS_RESTORE_DATABASE('%s', %d)", backupDir.getAbsolutePath(), backupId));
        ps.execute();
        System.out.println("Restore completed.");
    }
}
