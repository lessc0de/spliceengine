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

package com.splicemachine.si.impl.region;

/**
 * Decoder which decodes Transactions stored in the
 *
 * @author Scott Fines
 * Date: 8/18/14
 */

import com.google.protobuf.ByteString;
import com.splicemachine.encoding.Encoding;
import com.splicemachine.encoding.MultiFieldDecoder;
import com.splicemachine.encoding.MultiFieldEncoder;
import com.splicemachine.hbase.CellUtils;
import com.splicemachine.primitives.Bytes;
import com.splicemachine.si.api.txn.Txn;
import com.splicemachine.si.constants.SIConstants;
import com.splicemachine.si.coprocessor.TxnMessage;
import com.splicemachine.si.impl.TxnUtils;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.client.*;

import java.io.IOException;
import java.util.List;

public class V2TxnDecoder implements TxnDecoder{
    public static final V2TxnDecoder INSTANCE=new V2TxnDecoder();
    private static final byte[] FAMILY=SIConstants.DEFAULT_FAMILY_BYTES;
    static final byte[] DATA_QUALIFIER_BYTES=Bytes.toBytes("d");
    static final byte[] KEEP_ALIVE_QUALIFIER_BYTES=Bytes.toBytes("k");
    static final byte[] COMMIT_QUALIFIER_BYTES=Bytes.toBytes("t");
    static final byte[] GLOBAL_COMMIT_QUALIFIER_BYTES=Bytes.toBytes("g");
    static final byte[] STATE_QUALIFIER_BYTES=Bytes.toBytes("s");
    static final byte[] DESTINATION_TABLE_QUALIFIER_BYTES=Bytes.toBytes("e"); //had to pick a letter that was unique

    private V2TxnDecoder(){ } //singleton instance

    @Override
    public TxnMessage.Txn decode(RegionTxnStore txnStore,
                                 List<Cell> keyValues) throws IOException{
        if(keyValues.size()<=0) return null;
        Cell dataKv=null;
        Cell keepAliveKv=null;
        Cell commitKv=null;
        Cell globalCommitKv=null;
        Cell stateKv=null;
        Cell destinationTables=null;

        for(Cell kv : keyValues){
            if(CellUtils.singleMatchingColumn(kv,FAMILY,DATA_QUALIFIER_BYTES))
                dataKv=kv;
            else if(CellUtils.singleMatchingColumn(kv,FAMILY,KEEP_ALIVE_QUALIFIER_BYTES))
                keepAliveKv=kv;
            else if(CellUtils.singleMatchingColumn(kv,FAMILY,GLOBAL_COMMIT_QUALIFIER_BYTES))
                globalCommitKv=kv;
            else if(CellUtils.singleMatchingColumn(kv,FAMILY,COMMIT_QUALIFIER_BYTES))
                commitKv=kv;
            else if(CellUtils.singleMatchingColumn(kv,FAMILY,STATE_QUALIFIER_BYTES))
                stateKv=kv;
            else if(CellUtils.singleMatchingColumn(kv,FAMILY,DESTINATION_TABLE_QUALIFIER_BYTES))
                destinationTables=kv;
        }
        if(dataKv==null) return null;

        long txnId=TxnUtils.txnIdFromRowKey(dataKv.getRowArray(),dataKv.getRowOffset(),dataKv.getRowLength());
        return decodeInternal(txnStore,dataKv,keepAliveKv,commitKv,globalCommitKv,stateKv,destinationTables,txnId);
    }

    @Override
    public TxnMessage.Txn decode(RegionTxnStore txnStore,
                                 long txnId,Result result) throws IOException{
        Cell dataKv=result.getColumnLatestCell(FAMILY,DATA_QUALIFIER_BYTES);
        Cell commitTsVal=result.getColumnLatestCell(FAMILY,COMMIT_QUALIFIER_BYTES);
        Cell globalTsVal=result.getColumnLatestCell(FAMILY,GLOBAL_COMMIT_QUALIFIER_BYTES);
        Cell stateKv=result.getColumnLatestCell(FAMILY,STATE_QUALIFIER_BYTES);
        Cell destinationTables=result.getColumnLatestCell(FAMILY,DESTINATION_TABLE_QUALIFIER_BYTES);
        Cell kaTime=result.getColumnLatestCell(FAMILY,KEEP_ALIVE_QUALIFIER_BYTES);

        if(dataKv==null) return null;
        return decodeInternal(txnStore,dataKv,kaTime,commitTsVal,globalTsVal,stateKv,destinationTables,txnId);
    }

    protected long toLong(Cell data){
        return Encoding.decodeLong(data.getValueArray(),data.getValueOffset(),false);
    }

    protected TxnMessage.Txn decodeInternal(RegionTxnStore txnStore,
                                            Cell dataKv,Cell keepAliveKv,Cell commitKv,Cell globalCommitKv,
                                            Cell stateKv,Cell destinationTables,long txnId){
        MultiFieldDecoder decoder=MultiFieldDecoder.wrap(dataKv.getValueArray(),dataKv.getValueOffset(),dataKv.getValueLength());
        long beginTs=decoder.decodeNextLong();
        long parentTxnId=-1l;
        if(!decoder.nextIsNull()) parentTxnId=decoder.decodeNextLong();
        else decoder.skip();

        boolean isAdditive=false;
        boolean hasAdditive=true;
        if(!decoder.nextIsNull())
            isAdditive=decoder.decodeNextBoolean();
        else{
            hasAdditive=false;
            decoder.skip();
        }
        Txn.IsolationLevel level=null;
        if(!decoder.nextIsNull())
            level=Txn.IsolationLevel.fromByte(decoder.decodeNextByte());
        else decoder.skip();

        long commitTs=-1l;
        if(commitKv!=null)
            commitTs=toLong(commitKv);

        long globalTs=-1l;
        if(globalCommitKv!=null)
            globalTs=toLong(globalCommitKv);

        Txn.State state=Txn.State.decode(stateKv.getValueArray(),stateKv.getValueOffset(),stateKv.getValueLength());
        //adjust for committed timestamp
        if(commitTs>0 || globalTs>0){
            //we have a commit timestamp, our state MUST be committed
            state=Txn.State.COMMITTED;
        }

        if(state==Txn.State.ACTIVE){
                                /*
								 * We need to check that the transaction hasn't been timed out (and therefore rolled back). This
								 * happens if the keepAliveTime is older than the configured transaction timeout. Of course,
								 * there is some network latency which could cause small keep alives to be problematic. To help out,
								 * we allow a little fudge factor in the timeout
								 */
            state=txnStore.adjustStateForTimeout(state,keepAliveKv);
        }
        long kaTime=decodeKeepAlive(keepAliveKv,false);
        return composeValue(destinationTables,level,txnId,beginTs,parentTxnId,hasAdditive,
                isAdditive,commitTs,globalTs,state,kaTime);

    }

    protected static long decodeKeepAlive(Cell columnLatest,boolean oldForm){
        long lastKATime;
        if(oldForm){
            /*
             * The old version would put an empty value into the Keep Alive column. If the transaction
             * committed before the keep alive was initiated, then the field will still be null.
             *
             * Since we only read transactions in the old form, and don't create new ones, we just have to decide
             * what to do with these situations. They can only arise if the transaction either
             *
             * A) commits/rolls back before the keep alive can be initiated
             * B) fails before the first keep alive.
             *
             * In the case of a commit/roll back, the value of the keep alive doesn't matter, and in the case
             * of B), we want to fail it. The easiest way to deal with this is just to return 0l.
             */
            int length=columnLatest.getValueLength();
            if(length==0) return 0l;
            else
                lastKATime=Bytes.toLong(columnLatest.getValueArray(),columnLatest.getValueOffset(),length);
        }else
            lastKATime=Encoding.decodeLong(columnLatest.getValueArray(),columnLatest.getValueOffset(),false);
        return lastKATime;
    }

    /*
	 * Encodes transaction objects using the new, packed Encoding format
	 *
	 * The new way is a (more) compact representation which uses the Values CF (V) and compact qualifiers (using
	 * the Encoding.encodeX() methods) as follows:
	 *
	 * "d"	--	packed tuple of (beginTimestamp,parentTxnId,isDependent,additive,isolationLevel)
	 * "c"	--	counter (using a packed integer representation)
	 * "k"	--	keepAlive timestamp
	 * "t"	--	commit timestamp
	 * "g"	--	globalCommitTimestamp
	 * "s"	--	state
	 *
	 * The additional columns are kept separate so that they may be updated(and read) independently without
	 * reading and decoding the entire transaction.
	 *
	 * In the new format, if a transaction has been written to the table, then it automatically allows writes
	 *
	 * order: c,d,e,g,k,s,t
	 * order: counter,data,destinationTable,globalCommitTimestamp,keepAlive,state,commitTimestamp,
	 */
    public org.apache.hadoop.hbase.client.Put encodeForPut(TxnMessage.TxnInfo txnInfo,byte[] rowKey) throws IOException{
        org.apache.hadoop.hbase.client.Put put=new org.apache.hadoop.hbase.client.Put(rowKey);
        MultiFieldEncoder metaFieldEncoder=MultiFieldEncoder.create(5);
        metaFieldEncoder.encodeNext(txnInfo.getBeginTs()).encodeNext(txnInfo.getParentTxnid());

        if(txnInfo.hasIsAdditive())
            metaFieldEncoder.encodeNext(txnInfo.getIsAdditive());
        else
            metaFieldEncoder.encodeEmpty();

        Txn.IsolationLevel level=Txn.IsolationLevel.fromInt(txnInfo.getIsolationLevel());
        if(level!=null)
            metaFieldEncoder.encodeNext(level.encode());
        else
            metaFieldEncoder.encodeEmpty();

        Txn.State state=Txn.State.ACTIVE;

        put.add(FAMILY,DATA_QUALIFIER_BYTES,metaFieldEncoder.build());
        put.add(FAMILY,KEEP_ALIVE_QUALIFIER_BYTES,Encoding.encode(System.currentTimeMillis()));
        put.add(FAMILY,STATE_QUALIFIER_BYTES,state.encode());
        ByteString destTableBuffer=txnInfo.getDestinationTables();
        if(destTableBuffer!=null && !destTableBuffer.isEmpty())
            put.add(FAMILY,DESTINATION_TABLE_QUALIFIER_BYTES,destTableBuffer.toByteArray());
        return put;
    }

    protected TxnMessage.Txn composeValue(Cell destinationTables,
                                          Txn.IsolationLevel level,long txnId,long beginTs,long parentTs,boolean hasAdditive,
                                          boolean additive,long commitTs,long globalCommitTs,Txn.State state,long kaTime){
        return TXNDecoderUtils.composeValue(destinationTables,level,txnId,beginTs,parentTs,hasAdditive,additive,commitTs,globalCommitTs,state,kaTime);
    }

}
