package com.splicemachine.si.data.hbase;

import com.splicemachine.si.api.data.IHTable;
import com.splicemachine.si.api.data.SRowLock;
import com.splicemachine.si.api.data.STableWriter;
import com.splicemachine.utils.ByteSlice;
import com.splicemachine.utils.Pair;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Mutation;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.regionserver.OperationStatus;
import java.io.IOException;
import java.util.List;

public class HTableWriter implements STableWriter<Delete,Mutation,OperationStatus,Put,SRowLock,IHTable> {

    @Override
    public void write(IHTable table, Put put) throws IOException {
        table.put(put);
    }

    @Override
    public void write(IHTable table, Put put, SRowLock rowLock) throws IOException {
        table.put(put, rowLock);
    }

    @Override
    public void write(IHTable table, Put put, boolean durable) throws IOException {
        table.put(put, durable);
    }

    @Override
    public void write(IHTable table, List<Put> puts) throws IOException {
        table.put(puts);
    }

    @Override
    public OperationStatus[] writeBatch(IHTable table, Pair[] puts) throws IOException {
        return (OperationStatus[])table.batchPut(puts); // Need to fix..  JL-TODO
    }

    @Override
    public void delete(IHTable table, Delete delete, SRowLock rowLock) throws IOException {
        table.delete(delete, rowLock);
    }

    @Override
    public SRowLock tryLock(IHTable ihTable, byte[] rowKey) throws IOException {
        return ihTable.getLock(rowKey, false);
    }

    @Override
    public SRowLock tryLock(IHTable ihTable, ByteSlice rowKey) throws IOException {
        return ihTable.tryLock(rowKey);
    }

    @Override
    public boolean checkAndPut(IHTable table, byte[] family, byte[] qualifier, byte[] expectedValue, Put put) throws IOException {
        return table.checkAndPut(family, qualifier, expectedValue, put);
    }

    @Override
    public SRowLock lockRow(IHTable table, byte[] rowKey) throws IOException {
        return table.lockRow(rowKey);
    }

    @Override
    public void unLockRow(IHTable table, SRowLock lock) throws IOException {
        table.unLockRow(lock);
    }

    @Override
    public void increment(IHTable table, byte[] rowKey, byte[] family, byte[] qualifier, long amount) throws IOException {
        table.increment(rowKey,family,qualifier,amount);
    }
}