package com.splicemachine.si.testsetup;

import com.splicemachine.concurrent.Clock;
import com.splicemachine.si.api.txn.TxnStore;
import com.splicemachine.si.api.data.SDataLib;
import com.splicemachine.si.api.data.STableReader;
import com.splicemachine.si.api.data.STableWriter;
import com.splicemachine.si.constants.SIConstants;
import com.splicemachine.si.impl.data.light.IncrementingClock;
import com.splicemachine.si.impl.data.light.LDataLib;
import com.splicemachine.si.impl.data.light.LStore;
import com.splicemachine.si.impl.store.IgnoreTxnCacheSupplier;
import com.splicemachine.timestamp.api.TimestampSource;

public class LStoreSetup implements StoreSetup {

    private LStore store;
    private SDataLib dataLib;
    private STableReader reader;
    private STableWriter writer;
    private Clock clock;
    private TimestampSource source;
    private TxnStore txnStore;
    private IgnoreTxnCacheSupplier ignoreTxnStore;

    public LStoreSetup() {
        this.dataLib = new LDataLib();
        this.clock = new IncrementingClock(1_000);
        this.store = new LStore(clock);
        this.reader = store;
        this.writer = store;
        this.source = new SimpleTimestampSource();
        this.txnStore = new InMemoryTxnStore(source, SIConstants.transactionTimeout);
        this.ignoreTxnStore = new IgnoreTxnCacheSupplier();
    }

    @Override
    public SDataLib getDataLib() {
        return dataLib;
    }

    @Override
    public STableReader getReader() {
        return reader;
    }

    @Override
    public STableWriter getWriter() {
        return writer;
    }

    @Override
    public HBaseTestingUtility getTestCluster() {
        return null;
    }

    @Override
    public Object getStore() {
        return store;
    }

    @Override
    public String getPersonTableName() {
        return "person";
    }

    @Override
    public Clock getClock() {
        return clock;
    }

    @Override
    public TxnStore getTxnStore() {
        return txnStore;
    }

    @Override
    public IgnoreTxnCacheSupplier getIgnoreTxnStore() {
        return ignoreTxnStore;
    }

    @Override
    public TimestampSource getTimestampSource() {
        return source;
    }
}