package com.splicemachine.si.api;

import com.splicemachine.hbase.KVPair;
import com.splicemachine.si.impl.DDLFilter;
import com.splicemachine.si.impl.SICompactionState;
import com.splicemachine.si.impl.TxnFilter;
import com.splicemachine.storage.EntryPredicateFilter;
import org.apache.hadoop.hbase.regionserver.OperationStatus;

import java.io.IOException;
import java.util.Collection;

/**
 * Represents a "Transactional Region", that is, a region in Hbase which is transactionally aware.
 *
 * @author Scott Fines
 * Date: 7/1/14
 */
public interface TransactionalRegion {

		/**
		 * Create a new Transactional Filter for the region.
		 *
		 * This filter is "Unpacked", in the sense that it will not attempt to deal with packed
		 * data.
		 *
		 * @param txn the transaction to create a filter for
		 * @return a new transactional filter for the region
		 * @throws IOException if something goes wrong.
		 */
		TxnFilter unpackedFilter(Txn txn) throws IOException;

		TxnFilter packedFilter(Txn txn,EntryPredicateFilter predicateFilter,boolean countStar) throws IOException;

		DDLFilter ddlFilter(Txn ddlTxn) throws IOException;

		SICompactionState  compactionFilter() throws IOException;

		/**
		 * @return true if the underlying region is either closed or is closing
		 */
		boolean isClosed();

		boolean rowInRange(byte[] row);

		boolean containsRange(byte[] start, byte[] stop);

		String getTableName();

		void updateWriteRequests(long writeRequests);

		void updateReadRequests(long readRequests);

		OperationStatus[] bulkWrite(Txn txn,
																byte[] family, byte[] qualifier,
																ConstraintChecker constraintChecker,Collection<KVPair> data) throws IOException;


		String getRegionName();
}