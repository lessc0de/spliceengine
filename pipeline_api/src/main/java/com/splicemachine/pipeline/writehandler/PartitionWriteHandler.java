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

package com.splicemachine.pipeline.writehandler;

import com.splicemachine.access.api.NotServingPartitionException;
import com.splicemachine.access.api.RegionBusyException;
import com.splicemachine.access.api.WrongPartitionException;
import com.splicemachine.concurrent.ResettableCountDownLatch;
import com.splicemachine.kvpair.KVPair;
import com.splicemachine.pipeline.api.*;
import com.splicemachine.pipeline.constraint.BatchConstraintChecker;
import com.splicemachine.pipeline.client.WriteResult;
import com.splicemachine.pipeline.context.WriteContext;
import com.splicemachine.si.api.server.TransactionalRegion;
import com.splicemachine.si.api.txn.WriteConflict;
import com.splicemachine.si.constants.SIConstants;
import com.splicemachine.storage.MutationStatus;
import com.splicemachine.utils.SpliceLogUtils;
import org.apache.log4j.Logger;
import org.sparkproject.guava.base.Predicate;
import org.sparkproject.guava.collect.Collections2;
import org.sparkproject.guava.collect.Lists;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * @author Scott Fines
 *         Created on: 4/30/13
 */
public class PartitionWriteHandler implements WriteHandler {
    static final Logger LOG = Logger.getLogger(PartitionWriteHandler.class);
    protected final TransactionalRegion region;
    protected List<KVPair> mutations = Lists.newArrayList();
    protected ResettableCountDownLatch writeLatch;
    protected BatchConstraintChecker constraintChecker;

    public PartitionWriteHandler(TransactionalRegion region,
                                 ResettableCountDownLatch writeLatch,
                                 BatchConstraintChecker constraintChecker) {
        if (LOG.isDebugEnabled())
            SpliceLogUtils.debug(LOG, "regionWriteHandler create");
        this.region = region;
        this.writeLatch = writeLatch;
        this.constraintChecker = constraintChecker;
        this.mutations = Lists.newArrayList();
    }

    @Override
    public void next(KVPair kvPair, WriteContext ctx) {
        /*
         * Write-wise, we are at the end of the line, so make sure that we don't run through
         * another write-pipeline when the Region actually does it's writing
         */
        if (LOG.isTraceEnabled())
            SpliceLogUtils.trace(LOG, "next kvPair=%s, ctx=%s",kvPair,ctx);
        if(region.isClosed())
            ctx.failed(kvPair,WriteResult.notServingRegion());
        else if(!region.rowInRange(kvPair.rowKeySlice()))
            ctx.failed(kvPair, WriteResult.wrongRegion());
        else {
            if (kvPair.getType() == KVPair.Type.CANCEL){
                mutations.add(new KVPair(kvPair.getRowKey(), kvPair.getValue(), KVPair.Type.DELETE));
            }
            else
                mutations.add(kvPair);
            ctx.sendUpstream(kvPair);
        }
    }

    @Override
    public void flush(final WriteContext ctx) throws IOException {
        if (LOG.isDebugEnabled())
            SpliceLogUtils.debug(LOG, "flush");
        //make sure that the write aborts if the caller disconnects
        ctx.getCoprocessorEnvironment().ensureNetworkOpen();
        /*
         * We have to block here in case someone did a table manipulation under us.
         * If they didn't, then the writeLatch will be exhausted, and I'll be able to
         * go through without problems. Otherwise, I'll have to block until the metadata
         * manipulation is over before proceeding with my writes.
         */
        try {
            writeLatch.await();
        } catch (InterruptedException e) {
            //we've been interrupted! That's a problem, but what to do?
            //we'll have to fail everything, and rely on the system to retry appropriately
            //we can do that easily by just blowing up here
            throw new IOException(e);
        }
        //write all the puts first, since they are more likely
        Collection<KVPair> filteredMutations = Collections2.filter(mutations, new Predicate<KVPair>() {
            @Override
            public boolean apply(KVPair input) {
                return ctx.canRun(input);
            }
        });
        try {
            if (LOG.isTraceEnabled())
                SpliceLogUtils.trace(LOG, "Flush Writing rows=%d, table=%s", filteredMutations.size(), region.getTableName());
            doWrite(ctx, filteredMutations);
        } catch (IOException wce) {
            Throwable t = ctx.exceptionFactory().processPipelineException(wce);
            if(t instanceof WriteConflict){
                WriteResult result=new WriteResult(Code.WRITE_CONFLICT,wce.getMessage());
                for(KVPair mutation : filteredMutations){
                    ctx.result(mutation,result);
                }
            }else if(t instanceof NotServingPartitionException){
                WriteResult result = WriteResult.notServingRegion();
                for (KVPair mutation : filteredMutations) {
                    ctx.result(mutation, result);
                }
            }else if(t instanceof RegionBusyException){
                WriteResult result = WriteResult.regionTooBusy();
                for (KVPair mutation : filteredMutations) {
                    ctx.result(mutation, result);
                }
            }else if(t instanceof WrongPartitionException){
                //this shouldn't happen, but just in case
                WriteResult result = WriteResult.wrongRegion();
                for (KVPair mutation : filteredMutations) {
                    ctx.result(mutation, result);
                }
            } else{
                /*
                 * Paritition.put(Put[]) will throw an IOException
                 * if the WALEdit doesn't succeed, but only a single WALEdit write occurs,
                 * containing all the individual edits for the Pair[]. As a result, if we get an IOException,
                 * it's because we were unable to write ANY records to the WAL, so we can safely assume that
                 * all the puts failed and can be safely retried.
                 */
                WriteResult result=WriteResult.failed(wce.getClass().getSimpleName()+":"+wce.getMessage());
                for(KVPair mutation : filteredMutations){
                    ctx.result(mutation,result);
                }
            }
        } finally {
            filteredMutations.clear();
        }
    }

    private void doWrite(WriteContext ctx, Collection<KVPair> toProcess) throws IOException {
        assert toProcess!=null; //won't ever happen, but it's a nice safety check
        if (LOG.isTraceEnabled())
            SpliceLogUtils.trace(LOG, "doWrite {region=%s, records=%d}", ctx.getRegion().getName(),toProcess.size());

        Iterable<MutationStatus> status = region.bulkWrite(
                ctx.getTxn(),
                SIConstants.DEFAULT_FAMILY_BYTES,
                SIConstants.PACKED_COLUMN_BYTES,
                constraintChecker,
                toProcess
        );

        int i = 0;
        int failed = 0;
        Iterator<MutationStatus> statusIter = status.iterator();
        Iterator<KVPair> mutationIter = toProcess.iterator();
        while(statusIter.hasNext()){
            if(!mutationIter.hasNext())
                throw new IllegalStateException("MutationStatus result is not the same size as the mutations collection!");
            MutationStatus stat = statusIter.next();
            KVPair mutation = mutationIter.next();
            if(stat.isNotRun())
                ctx.notRun(mutation);
            else if(stat.isSuccess()){
                ctx.success(mutation);
            } else{
                //assume it's a failure
                //see if it's due to constraints, otherwise just pass it through
                if (constraintChecker != null && constraintChecker.matches(stat)) {
                    ctx.result(mutation, constraintChecker.asWriteResult(stat));
                    break;
                }else{
                    failed++;
                    ctx.failed(mutation, WriteResult.failed(stat.errorMessage()));
                }
            }
        }

        region.updateWriteRequests(toProcess.size() - failed);
    }


    @Override
    public void close(WriteContext ctx) throws IOException {
        if (LOG.isDebugEnabled())
            SpliceLogUtils.debug(LOG, "close");
        mutations.clear();
        mutations = null; // Dereference
    }

}
