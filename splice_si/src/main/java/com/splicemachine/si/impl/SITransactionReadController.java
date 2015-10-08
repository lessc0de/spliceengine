package com.splicemachine.si.impl;

import com.google.common.collect.Lists;
import com.splicemachine.constants.FixedSIConstants;import com.splicemachine.si.api.*;
import com.splicemachine.si.data.api.SDataLib;
import com.splicemachine.si.impl.store.IgnoreTxnCacheSupplier;
import com.splicemachine.storage.EntryDecoder;
import com.splicemachine.storage.EntryPredicateFilter;
//import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.OperationWithAttributes;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.filter.Filter;
import java.io.IOException;
import java.util.List;

/**
 * @author Scott Fines
 *         Date: 2/13/14
 */
public class SITransactionReadController<Data,
				Get extends OperationWithAttributes,
				Scan extends OperationWithAttributes,
				Delete extends OperationWithAttributes,
				Put extends OperationWithAttributes
				>
				implements TransactionReadController<Data,Get,Scan>{
		private final DataStore dataStore;
		private final SDataLib dataLib;
		private final TxnSupplier txnSupplier;
        private final IgnoreTxnCacheSupplier ignoreTxnCacheSupplier;

    public SITransactionReadController(DataStore dataStore,
                                       SDataLib dataLib,
                                       TxnSupplier txnSupplier,
                                       IgnoreTxnCacheSupplier ignoreTxnCacheSupplier) {
				this.dataStore = dataStore;
				this.dataLib = dataLib;
				this.txnSupplier = txnSupplier;
                this.ignoreTxnCacheSupplier = ignoreTxnCacheSupplier;
		}

		@Override
		@SuppressWarnings("unchecked")
		public SIReadRequest readRequest(OperationWithAttributes get) {
			if(dataStore.isSuppressIndexing(get)) return SIReadRequest.NO_SI;

			byte[] siNeededAttribute = dataStore.getSINeededAttribute(get);
            return SIReadRequest.readRequest(siNeededAttribute);
		}

		@Override
		@SuppressWarnings("unchecked")
		public void preProcessGet(Get get) throws IOException {
				dataLib.setGetTimeRange(get, 0, Long.MAX_VALUE);
				dataLib.setGetMaxVersions(get);
		}

		@Override
		@SuppressWarnings("unchecked")
		public void preProcessScan(Scan scan) throws IOException {
				dataLib.setScanTimeRange(scan, 0, Long.MAX_VALUE);
				dataLib.setScanMaxVersions(scan);
		}

		@Override
		public TxnFilter newFilterState(Txn txn) throws IOException {
				return newFilterState(null,txn);
		}

		@Override
		public TxnFilter newFilterState(ReadResolver readResolver, Txn txn) throws IOException {
				return new SimpleTxnFilter(null, txnSupplier,ignoreTxnCacheSupplier,txn,readResolver,dataStore);
		}

		@Override
		public TxnFilter newFilterStatePacked(ReadResolver readResolver,
																						 EntryPredicateFilter predicateFilter, Txn txn, boolean countStar) throws IOException {
			return new PackedTxnFilter(newFilterState(txn),
					SIFactoryDriver.siFactory.getRowAccumulator(predicateFilter,new EntryDecoder(),countStar));
		}

		@Override
		@SuppressWarnings("unchecked")
		public Filter.ReturnCode filterKeyValue(TxnFilter filterState, Data data) throws IOException {
				return filterState.filterKeyValue(data);
		}

		@Override
		public void filterNextRow(TxnFilter filterState) {
				filterState.nextRow();
		}

		@Override
		@SuppressWarnings("unchecked")
		public Result filterResult(TxnFilter filterState, Result result) throws IOException {
				//TODO -sf- this is only used in testing--ignore when production tuning
				final SDataLib<Data, Put, Delete, Get, Scan> dataLib = dataStore.dataLib;
				final List<Data> filteredCells = Lists.newArrayList();
				final List<Data> KVs = dataLib.listResult(result);
				if (KVs != null) {
//						byte[] currentRowKey = null;
						for (Data kv : KVs) {
							filterKeyValue(filterState, kv);
						}
						if (!filterState.getExcludeRow())
							filteredCells.add((Data) filterState.produceAccumulatedKeyValue());
				}
				if (filteredCells.isEmpty()) {
					return null;
				} else {
					return dataStore.dataLib.newResult(filteredCells);
				}
		}

		@Override
		public DDLFilter newDDLFilter(TxnView txn) throws IOException {
        return new DDLFilter(txn);
		}


}
