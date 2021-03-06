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

package com.splicemachine.si.impl;

import com.splicemachine.access.api.PartitionFactory;
import com.splicemachine.concurrent.IncrementingClock;
import com.splicemachine.encoding.Encoding;
import com.splicemachine.impl.MockRegionUtils;
import com.splicemachine.si.api.readresolve.ReadResolver;
import com.splicemachine.si.api.txn.Txn;
import com.splicemachine.si.api.txn.TxnLifecycleManager;
import com.splicemachine.si.api.txn.TxnStore;
import com.splicemachine.si.constants.SIConstants;
import com.splicemachine.si.data.HExceptionFactory;
import com.splicemachine.si.impl.readresolve.SynchronousReadResolver;
import com.splicemachine.si.impl.rollforward.RollForwardStatus;
import com.splicemachine.si.impl.store.TestingTimestampSource;
import com.splicemachine.si.impl.store.TestingTxnStore;
import com.splicemachine.si.impl.txn.ReadOnlyTxn;
import com.splicemachine.si.impl.txn.WritableTxn;
import com.splicemachine.storage.DataFilter;
import com.splicemachine.storage.HCell;
import com.splicemachine.storage.RegionPartition;
import com.splicemachine.utils.GreenLight;
import com.splicemachine.utils.TrafficControl;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.regionserver.HRegion;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Tests around the possibilities for the SynchronousReadResolver
 *
 * @author Scott Fines
 *         Date: 7/2/14
 */
public class SynchronousReadResolverTest {

    @Test
    public void testResolveRolledBackWorks() throws Exception {
        HRegion region = MockRegionUtils.getMockRegion();
        RegionPartition rp = new RegionPartition(region);
        TrafficControl control = GreenLight.INSTANCE;

        final TxnStore store = new TestingTxnStore(new IncrementingClock(),new TestingTimestampSource(),HExceptionFactory.INSTANCE,Long.MAX_VALUE);
        ReadResolver resolver = SynchronousReadResolver.getResolver(rp, store, new RollForwardStatus(), control, false);
        TxnLifecycleManager tc = mock(TxnLifecycleManager.class);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
                store.rollback((Long) invocationOnMock.getArguments()[0]);
                return null;
            }
        }).when(tc).rollback(1l);

        Txn rolledBackTxn = new WritableTxn(1l, 1l, Txn.IsolationLevel.SNAPSHOT_ISOLATION, Txn.ROOT_TRANSACTION, tc, false,HExceptionFactory.INSTANCE);
        store.recordNewTransaction(rolledBackTxn);
        rolledBackTxn.rollback(); //ensure that it's rolled back

        byte[] rowKey = Encoding.encode("hello");
        Put testPut = new Put(rowKey);
        testPut.add(SIConstants.DEFAULT_FAMILY_BYTES,
                SIConstants.PACKED_COLUMN_BYTES,
                rolledBackTxn.getTxnId(), Encoding.encode("hello2"));

        region.put(testPut);

        Txn readTxn = ReadOnlyTxn.createReadOnlyTransaction(2l, Txn.ROOT_TRANSACTION, 2l,
                Txn.IsolationLevel.SNAPSHOT_ISOLATION, false, mock(TxnLifecycleManager.class),HExceptionFactory.INSTANCE);
        SimpleTxnFilter filter = new SimpleTxnFilter(null, readTxn,resolver,store);

        Result result = region.get(new Get(rowKey));
        Assert.assertEquals("Incorrect result size", 1, result.size());
        Cell kv = result.getColumnLatestCell(SIConstants.DEFAULT_FAMILY_BYTES, SIConstants.PACKED_COLUMN_BYTES);
        Assert.assertNotNull("No data column found!", kv);

        DataFilter.ReturnCode returnCode = filter.filterCell(new HCell(kv));
        Assert.assertEquals("Incorrect return code!", DataFilter.ReturnCode.SKIP, returnCode);

        //check to see if the resolver added the proper key value
        result = region.get(new Get(rowKey));
        Assert.assertEquals("Incorrect result size after read resolve!", 0, result.size());
    }

    @Test
    public void testResolvingCommittedWorks() throws Exception {
        HRegion region = MockRegionUtils.getMockRegion();
        RegionPartition rp = new RegionPartition(region);

        final TestingTimestampSource commitTsGenerator = new TestingTimestampSource();
        final TxnStore store = new TestingTxnStore(new IncrementingClock(),commitTsGenerator,HExceptionFactory.INSTANCE,Long.MAX_VALUE);
        ReadResolver resolver = SynchronousReadResolver.getResolver(rp,store,new RollForwardStatus(),GreenLight.INSTANCE,false);
        TxnLifecycleManager tc = mock(TxnLifecycleManager.class);
        doAnswer(new Answer<Long>() {
            @Override
            public Long answer(InvocationOnMock invocationOnMock) throws Throwable {
                long next = commitTsGenerator.nextTimestamp();
                store.commit((Long) invocationOnMock.getArguments()[0]);
                return next + 1;
            }
        }).when(tc).commit(anyLong());
        Txn committedTxn = new WritableTxn(1l, 1l, Txn.IsolationLevel.SNAPSHOT_ISOLATION, Txn.ROOT_TRANSACTION, tc, false,HExceptionFactory.INSTANCE);
        store.recordNewTransaction(committedTxn);
        committedTxn.commit();

        byte[] rowKey = Encoding.encode("hello");
        Put testPut = new Put(rowKey);
        testPut.add(SIConstants.DEFAULT_FAMILY_BYTES,
                SIConstants.PACKED_COLUMN_BYTES,
                committedTxn.getTxnId(), Encoding.encode("hello2"));

        region.put(testPut);

        Txn readTxn = ReadOnlyTxn.createReadOnlyTransaction(3l, Txn.ROOT_TRANSACTION, 3l,
                Txn.IsolationLevel.SNAPSHOT_ISOLATION, false, mock(TxnLifecycleManager.class),HExceptionFactory.INSTANCE);
        SimpleTxnFilter filter = new SimpleTxnFilter(null, readTxn,resolver,store);

        Result result = region.get(new Get(rowKey));
        Assert.assertEquals("Incorrect result size", 1, result.size());
        Cell kv = result.getColumnLatestCell(SIConstants.DEFAULT_FAMILY_BYTES, SIConstants.PACKED_COLUMN_BYTES);
        Assert.assertNotNull("No data column found!", kv);

        DataFilter.ReturnCode returnCode = filter.filterCell(new HCell(kv));
        Assert.assertEquals("Incorrect return code!", DataFilter.ReturnCode.INCLUDE, returnCode);

        //check to see if the resolver added the proper key value
        result = region.get(new Get(rowKey));
        Assert.assertEquals("Incorrect result size after read resolve!", 2, result.size());
        Cell commitTs = result.getColumnLatestCell(SIConstants.DEFAULT_FAMILY_BYTES, SIConstants.SNAPSHOT_ISOLATION_COMMIT_TIMESTAMP_COLUMN_BYTES);
        Assert.assertNotNull("No Commit TS column found!", commitTs);
        Assert.assertEquals("Incorrect committed txnId", committedTxn.getTxnId(), commitTs.getTimestamp());
        Assert.assertEquals("Incorrect commit timestamp!", committedTxn.getEffectiveCommitTimestamp(), Bytes.toLong(CellUtil.cloneValue(commitTs)));
    }

    @Test
    public void testResolvingCommittedDoesNotHappenUntilParentCommits() throws Exception {
        HRegion region = MockRegionUtils.getMockRegion();
        RegionPartition rp = new RegionPartition(region);

        TestingTimestampSource timestampSource = new TestingTimestampSource();
        TxnStore store = new TestingTxnStore(new IncrementingClock(),timestampSource,HExceptionFactory.INSTANCE,Long.MAX_VALUE);
        ReadResolver resolver = SynchronousReadResolver.getResolver(rp, store, new RollForwardStatus(), GreenLight.INSTANCE, false);

        ClientTxnLifecycleManager tc = new ClientTxnLifecycleManager(timestampSource,HExceptionFactory.INSTANCE);
        tc.setTxnStore(store);
        tc.setKeepAliveScheduler(new ManualKeepAliveScheduler(store));
        Txn parentTxn = tc.beginTransaction(Bytes.toBytes("1184"));

        Txn childTxn = tc.beginChildTransaction(parentTxn, Txn.IsolationLevel.SNAPSHOT_ISOLATION, false, Bytes.toBytes("1184"));

        byte[] rowKey = Encoding.encode("hello");
        Put testPut = new Put(rowKey);
        testPut.add(SIConstants.DEFAULT_FAMILY_BYTES,
                SIConstants.PACKED_COLUMN_BYTES,
                childTxn.getTxnId(), Encoding.encode("hello2"));

        region.put(testPut);

        childTxn.commit();

        Txn readTxn = tc.beginTransaction(); //a read-only transaction with SI semantics
        SimpleTxnFilter filter = new SimpleTxnFilter(null, readTxn,resolver,store);

        Result result = region.get(new Get(rowKey));
        Assert.assertEquals("Incorrect result size", 1, result.size());
        Cell kv = result.getColumnLatestCell(SIConstants.DEFAULT_FAMILY_BYTES, SIConstants.PACKED_COLUMN_BYTES);
        Assert.assertNotNull("No data column found!", kv);

        DataFilter.ReturnCode returnCode = filter.filterCell(new HCell(kv));
        Assert.assertEquals("Incorrect return code!", DataFilter.ReturnCode.SKIP, returnCode);

        //make sure the resolver has not added anything
        result = region.get(new Get(rowKey));
        Assert.assertEquals("Incorrect result size after read resolve!", 1, result.size());

        //commit the parent and see if resolution works then
        parentTxn.commit();

        //now re-read the data and make sure that it resolves
        filter.nextRow();
        result = region.get(new Get(rowKey));
        Assert.assertEquals("Incorrect result size", 1, result.size());
        kv = result.getColumnLatestCell(SIConstants.DEFAULT_FAMILY_BYTES, SIConstants.PACKED_COLUMN_BYTES);
        Assert.assertNotNull("No data column found!", kv);

        returnCode = filter.filterCell(new HCell(kv));
        Assert.assertEquals("Incorrect return code!", DataFilter.ReturnCode.SKIP, returnCode);

        //make sure that the read-resolver worked
        result = region.get(new Get(rowKey));
        Assert.assertEquals("Incorrect result size", 2, result.size());
        kv = result.getColumnLatestCell(SIConstants.DEFAULT_FAMILY_BYTES, SIConstants.PACKED_COLUMN_BYTES);
        Assert.assertNotNull("No data column found!", kv);
        Cell commitTs = result.getColumnLatestCell(SIConstants.DEFAULT_FAMILY_BYTES, SIConstants.SNAPSHOT_ISOLATION_COMMIT_TIMESTAMP_COLUMN_BYTES);
        Assert.assertNotNull("No Commit TS column found!", commitTs);
        Assert.assertEquals("Incorrect committed txnId", childTxn.getTxnId(), commitTs.getTimestamp());
        Assert.assertEquals("Incorrect commit timestamp!", childTxn.getEffectiveCommitTimestamp(), Bytes.toLong(CellUtil.cloneValue(commitTs)));
    }
}
