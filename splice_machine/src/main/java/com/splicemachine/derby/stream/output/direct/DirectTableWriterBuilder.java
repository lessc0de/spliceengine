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

package com.splicemachine.derby.stream.output.direct;

import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.derby.stream.iapi.OperationContext;
import com.splicemachine.derby.stream.iapi.TableWriter;
import com.splicemachine.derby.stream.output.DataSetWriterBuilder;
import com.splicemachine.primitives.Bytes;
import com.splicemachine.si.api.txn.TxnView;
import com.splicemachine.si.impl.driver.SIDriver;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.SerializationUtils;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * @author Scott Fines
 *         Date: 1/13/16
 */
public abstract class DirectTableWriterBuilder implements Externalizable,DataSetWriterBuilder{
    protected long destConglomerate;
    protected TxnView txn;
    protected OperationContext opCtx;
    protected boolean skipIndex;

    @Override
    public DataSetWriterBuilder destConglomerate(long heapConglom){
        this.destConglomerate = heapConglom;
        return this;
    }

    @Override
    public DataSetWriterBuilder txn(TxnView txn){
        this.txn = txn;
        return this;
    }

    @Override
    public DataSetWriterBuilder operationContext(OperationContext operationContext){
        this.opCtx = operationContext;
        return this;
    }

    @Override
    public DataSetWriterBuilder skipIndex(boolean skipIndex){
        this.skipIndex = skipIndex;
        return this;
    }

    @Override
    public TxnView getTxn(){
        return txn;
    }

    @Override
    public byte[] getDestinationTable(){
        return Bytes.toBytes(Long.toString(destConglomerate));
    }

    @Override
    public TableWriter buildTableWriter() throws StandardException{
        return new DirectPipelineWriter(destConglomerate,txn,opCtx,skipIndex);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException{
        out.writeLong(destConglomerate);
        out.writeObject(opCtx);
        out.writeBoolean(skipIndex);
        SIDriver.driver().getOperationFactory().writeTxn(txn,out);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException{
        destConglomerate = in.readLong();
        opCtx = (OperationContext)in.readObject();
        skipIndex = in.readBoolean();
        txn = SIDriver.driver().getOperationFactory().readTxn(in);
    }

    public String base64Encode(){
        return Base64.encodeBase64String(SerializationUtils.serialize(this));
    }

    public static DirectTableWriterBuilder decodeBase64(String base64){
        byte[] bytes=Base64.decodeBase64(base64);
        return (DirectTableWriterBuilder)SerializationUtils.deserialize(bytes);
    }
}
