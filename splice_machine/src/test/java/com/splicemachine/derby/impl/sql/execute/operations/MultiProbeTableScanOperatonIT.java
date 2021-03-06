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

package com.splicemachine.derby.impl.sql.execute.operations;

import com.splicemachine.derby.test.framework.*;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Unit Test for making sure MultiProbeTableScanOperation is logically correct.  Once we have metrics information,
 * the test should be expanded to show that we only filter the records required.
 *
 */
public class MultiProbeTableScanOperatonIT extends SpliceUnitTest {
	public static final String CLASS_NAME = MultiProbeTableScanOperatonIT.class.getSimpleName();
	protected static SpliceWatcher spliceClassWatcher = new SpliceWatcher(CLASS_NAME);
	protected static SpliceSchemaWatcher schemaWatcher = new SpliceSchemaWatcher(CLASS_NAME);
	protected static SpliceTableWatcher t1Watcher = new SpliceTableWatcher("user_groups",schemaWatcher.schemaName,"(user_id BIGINT NOT NULL,segment_id INT NOT NULL,unixtime BIGINT, primary key(segment_id, user_id))");
	protected static SpliceTableWatcher t2Watcher = new SpliceTableWatcher("docs",schemaWatcher.schemaName,"(id varchar(128) not null)");
	protected static SpliceTableWatcher t3Watcher = new SpliceTableWatcher("colls",schemaWatcher.schemaName,"(id varchar(128) not null,collid smallint not null)");
	protected static SpliceTableWatcher t4Watcher = new SpliceTableWatcher("b",schemaWatcher.schemaName,"(d decimal(10))");
    protected static SpliceTableWatcher t5Watcher = new SpliceTableWatcher("a",schemaWatcher.schemaName,"(d decimal(10,0))");
    protected static SpliceIndexWatcher i5Watcher = new SpliceIndexWatcher("a",schemaWatcher.schemaName,"i",schemaWatcher.schemaName,"(d)");


	private static int size = 10;

	@ClassRule
	public static TestRule chain = RuleChain.outerRule(spliceClassWatcher)
			.around(schemaWatcher)
			.around(t1Watcher)
			.around(t2Watcher)
			.around(t3Watcher)
			.around(t4Watcher)
            .around(t5Watcher)
            .around(i5Watcher)
			.around(new SpliceDataWatcher() {
				@Override
				protected void starting(Description description) {
					try {
						PreparedStatement ps = spliceClassWatcher.prepareStatement("insert into " + t1Watcher.toString() + " values (?,?,?)");
						for (int i = 0; i < size; i++) {
							ps.setInt(1, i);
							ps.setInt(2, i);
							ps.setLong(3, 1l);
							ps.execute();
						}

						for (int i = 0; i < size; i++) {
							if ((i == 4) || (i == 6)) {
								ps.setInt(1, size + i);
								ps.setInt(2, i);
								ps.setLong(3, 1l);
								ps.execute();
							}
						}

						ps = spliceClassWatcher.prepareStatement("insert into " + t2Watcher.toString() + " values (?)");
						ps.setString(1, "24");
						ps.addBatch();
						ps.setString(1, "25");
						ps.addBatch();
						ps.setString(1, "36");
						ps.addBatch();
						ps.setString(1, "27");
						ps.addBatch();
						ps.setString(1, "124");
						ps.addBatch();
						ps.setString(1, "567");
						ps.addBatch();
						ps.executeBatch();

						ps = spliceClassWatcher.prepareStatement("insert into " + t3Watcher.toString() + " values (?,?)");
						ps.setString(1, "123");
						ps.setShort(2, (short) 2);
						ps.addBatch();
						ps.setString(1, "124");
						ps.setShort(2, (short) -5);
						ps.addBatch();
						ps.setString(1, "24");
						ps.setShort(2, (short) 1);
						ps.addBatch();
						ps.setString(1, "26");
						ps.setShort(2, (short) -2);
						ps.addBatch();
						ps.setString(1, "36");
						ps.setShort(2, (short) 1);
						ps.addBatch();
						ps.setString(1, "37");
						ps.setShort(2, (short) 8);
						ps.addBatch();
						ps.executeBatch();

						ps = spliceClassWatcher.prepareStatement("insert into " + t4Watcher.toString() + " values (?)");
						for (int i = 0; i <= 10; ++i) {
							ps.setInt(1, i);
							ps.addBatch();
						}
						ps.executeBatch();
					} catch (Exception e) {
						throw new RuntimeException(e);
					} finally {
						spliceClassWatcher.closeAll();
					}
				}

			});

	@Rule public SpliceWatcher methodWatcher = new SpliceWatcher(CLASS_NAME);

	@Test
	public void testMultiProbeTableScanScroll() throws Exception {
		ResultSet rs = methodWatcher.executeQuery("select user_id from "+t1Watcher+" where segment_id in (1,5,8,12)");
		int i = 0;
		while (rs.next()) {
			i++;
		}
		Assert.assertEquals("Incorrect count returned!",3,i);
	}

	@Test
	//DB-2575
	public void testMultiProbeTableScanWithEqualPredicate() throws Exception {
		ResultSet rs = methodWatcher.executeQuery("select user_id from "+t1Watcher+" where segment_id in (1,5,8,12) and unixtime = 1");
		int i = 0;
		while (rs.next()) {
			i++;
		}
		Assert.assertEquals("Incorrect count returned!",3,i);
	}

	@Test
	public void testMultiProbeTableScanSink() throws Exception {
		ResultSet rs = methodWatcher.executeQuery("select count(user_id) from (" +
				"select user_id, ("+
				"max(case when segment_id = 7 then true else false end) " +
				"or " +
				"max(case when segment_id = 4 then true else false end)" +
				") as in_segment " +
				"from "+t1Watcher+ " " +
				"where segment_id in (7, 4) " +
				"group by user_id) foo where in_segment = true");
		int i = 0;
		while (rs.next()) {
			i++;
			Assert.assertEquals("Incorrect Distinct Customers",3,rs.getLong(1));
		}
		Assert.assertEquals("Incorrect records returned!",1,i);
	}

	@Test
	public void testMultiProbeInSubQueryWithIndex() throws Exception {
				/* Regression test for DB-1040 */
		SpliceIndexWatcher indexWatcher = new SpliceIndexWatcher(t3Watcher.tableName, t3Watcher.getSchema(),"new_index_3",t3Watcher.getSchema(),"(collid)");
		indexWatcher.starting(null);
		try{
			ResultSet rs = methodWatcher.executeQuery("select count(id) from docs where id > any (select id from colls where collid in (-2,1))");
			Assert.assertTrue("No results returned!",rs.next());
			int count = rs.getInt(1);
			Assert.assertEquals("Incorrect count returned!",4,count);
			Assert.assertFalse("Too many rows returned!",rs.next());
		}finally{
			indexWatcher.drop();
		}
	}

	@Test
	//DB-4854
	public void testMultiProbeIntegerValue() throws Exception {
		SpliceIndexWatcher indexWatcher = new SpliceIndexWatcher(t4Watcher.tableName, t4Watcher.getSchema(),"idxb",t4Watcher.getSchema(),"(d)");
		indexWatcher.starting(null);
		ResultSet rs = methodWatcher.executeQuery("select count(*) from b where d in (9,10)");
		Assert.assertTrue(rs.next());
		Assert.assertTrue("wrong count", rs.getInt(1) == 2);

		rs = methodWatcher.executeQuery("select count(*) from b where d in (9)");
		Assert.assertTrue(rs.next());
		Assert.assertTrue("wrong count", rs.getInt(1)==1);
	}

	@Test
	//DB-5349
	public void testMultiProbeTableScanWithProbeVariables() throws Exception {
		PreparedStatement ps = methodWatcher.prepareStatement("select user_id from "+t1Watcher+" where segment_id in (?,?,?,?) and unixtime = ?");
		ps.setInt(1,1);
		ps.setInt(2,5);
		ps.setInt(3,8);
		ps.setInt(4,12);
		ps.setLong(5,1);
		ResultSet rs = ps.executeQuery();
		int i = 0;
		while (rs.next()) {
			i++;
		}
		Assert.assertEquals("Incorrect count returned!",3,i);
	}


	// DB-4857
    @Test
    public void testMultiProbeWithComputations() throws Exception {
        this.thirdRowContainsQuery("explain select * from a --splice-properties index=i\n" +
                " where d in (10.0+10, 11.0+10)","preds=[(D[0:1] IN ((10.0 + 10),(11.0 + 10)))]",methodWatcher);
    }
}
