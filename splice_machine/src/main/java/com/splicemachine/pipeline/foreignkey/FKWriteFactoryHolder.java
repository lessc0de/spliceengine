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

package com.splicemachine.pipeline.foreignkey;

import com.splicemachine.ddl.DDLMessage;
import com.splicemachine.derby.ddl.DDLUtils;
import org.sparkproject.guava.collect.ImmutableList;
import org.sparkproject.guava.collect.Lists;
import org.sparkproject.guava.collect.Maps;
import org.sparkproject.guava.primitives.Ints;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.sql.dictionary.*;
import com.splicemachine.ddl.DDLMessage.*;
import com.splicemachine.derby.utils.DataDictionaryUtils;
import com.splicemachine.pipeline.api.PipelineExceptionFactory;
import com.splicemachine.pipeline.context.PipelineWriteContext;
import com.splicemachine.pipeline.contextfactory.LocalWriteFactory;
import com.splicemachine.pipeline.contextfactory.WriteFactoryGroup;
import com.splicemachine.protobuf.ProtoUtil;
import com.splicemachine.si.api.data.TxnOperationFactory;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factored out of LocalWriteContextFactory, this class holds the WriteFactory instances related to foreign keys and
 * methods for creating them.
 */
public class FKWriteFactoryHolder implements WriteFactoryGroup{

    private final PipelineExceptionFactory exceptionFactory;
    private final TxnOperationFactory txnOperationFactory;
    /*
     * Foreign key WriteHandlers intercept writes to parent/child tables and send them to the corresponding parent/child
     * table for existence checks. Generally one WriteHandler handles all intercepts/checks for the conglomerate
     * for this context.  Child intercept is the exception, where we will have multiple WriteHandlers if the backing
     * index is shared by multiple FKs (multiple FKs on the same child column sharing the the same backing index).
     */
    private volatile Map<Long, ForeignKeyChildInterceptWriteFactory> childInterceptWriteFactories = new ConcurrentHashMap<>();
    private ForeignKeyParentInterceptWriteFactory parentInterceptWriteFactory;

    public FKWriteFactoryHolder(PipelineExceptionFactory exceptionFactory,TxnOperationFactory txnOperationFactory){
        this.exceptionFactory=exceptionFactory;
        this.txnOperationFactory = txnOperationFactory;
    }

    @Override
    public void addFactory(LocalWriteFactory writeFactory){

    }

    @Override
    public void addFactories(PipelineWriteContext context,boolean keepState,int expectedWrites) throws IOException{
        if(hasChildCheck()){
            List<ForeignKeyChildInterceptWriteFactory> childInterceptWriteFactories=getChildInterceptWriteFactories();
            for(ForeignKeyChildInterceptWriteFactory fkFactory:childInterceptWriteFactories){
               fkFactory.addTo(context,false,expectedWrites);
            }
        }
        if(hasParentIntercept()){
            getParentInterceptWriteFactory().addTo(context,false,expectedWrites);
        }
    }

    @Override
    public void replace(LocalWriteFactory newFactory){
        addFactory(newFactory);
    }

    @Override
    public void clear(){

    }

    @Override
    public boolean isEmpty(){
        return false;
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    //
    // build factories from minimal information (used tasks/jobs to ALTER existing write contexts)
    //
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    public void addParentInterceptWriteFactory(String parentTableName, List<Long> backingIndexConglomIds,List<FKConstraintInfo> fkConstraintInfos) {
        /* One instance handles all FKs that reference this primary key or unique index */
        if (parentInterceptWriteFactory == null) {
            parentInterceptWriteFactory = new ForeignKeyParentInterceptWriteFactory(parentTableName, backingIndexConglomIds,exceptionFactory,fkConstraintInfos);
        }
    }

    public void addChildIntercept(long referencedConglomerateNumber, FKConstraintInfo fkConstraintInfo) {
        childInterceptWriteFactories.put(referencedConglomerateNumber, new ForeignKeyChildInterceptWriteFactory(referencedConglomerateNumber, fkConstraintInfo,exceptionFactory));
    }

    /**
     * Convenience method that takes the DDLChange and the conglom on which we are called and invokes the methods
     * above to add factories for the parent or child table as appropriate.
     */
    public void handleForeignKeyAdd(DDLChange ddlChange, long onConglomerateNumber) {
        TentativeFK tentativeFKAdd = ddlChange.getTentativeFK();
        // We are configuring a write context on the PARENT base-table or unique-index.
        if (onConglomerateNumber == tentativeFKAdd.getReferencedConglomerateNumber()) {
            addParentInterceptWriteFactory(tentativeFKAdd.getReferencedTableName(), ImmutableList.of(tentativeFKAdd.getReferencingConglomerateNumber()),ImmutableList.of(tentativeFKAdd.getFkConstraintInfo()));
        }
        // We are configuring a write context on the CHILD fk backing index.
        if (onConglomerateNumber == tentativeFKAdd.getReferencingConglomerateNumber()) {
            addChildIntercept(tentativeFKAdd.getReferencedConglomerateNumber(), tentativeFKAdd.getFkConstraintInfo());
        }
    }

    public void handleForeignKeyDrop(DDLChange ddlChange, long onConglomerateNumber) {
        TentativeFK tentativeFKAdd = ddlChange.getTentativeFK();
        // We are configuring a write context on the PARENT base-table or unique-index.
        if (onConglomerateNumber == tentativeFKAdd.getReferencedConglomerateNumber()) {
            if (parentInterceptWriteFactory != null) {
                parentInterceptWriteFactory.removeReferencingIndexConglomerateNumber(tentativeFKAdd.getReferencingConglomerateNumber());
            }
        }
        // We are configuring a write context on the CHILD fk backing index.
        if (onConglomerateNumber == tentativeFKAdd.getReferencingConglomerateNumber()) {
            childInterceptWriteFactories.remove(tentativeFKAdd.getReferencedConglomerateNumber());
        }
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    //
    // build factories from constraints (used when we are creating the FK from metadata on context startup)
    //
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    /* Add factories for intercepting writes to FK backing indexes. */
    public void buildForeignKeyInterceptWriteFactory(DataDictionary dataDictionary, ForeignKeyConstraintDescriptor fkConstraintDesc) throws StandardException {
        ReferencedKeyConstraintDescriptor referencedConstraint = fkConstraintDesc.getReferencedConstraint();
        long referencedKeyConglomerateNum;
        // FK references unique constraint.
        if (referencedConstraint.getConstraintType() == DataDictionary.UNIQUE_CONSTRAINT) {
            referencedKeyConglomerateNum = referencedConstraint.getIndexConglomerateDescriptor(dataDictionary).getConglomerateNumber();
        }
        // FK references primary key constraint.
        else {
            referencedKeyConglomerateNum = referencedConstraint.getTableDescriptor().getHeapConglomerateId();
        }
        FKConstraintInfo info = ProtoUtil.createFKConstraintInfo(fkConstraintDesc);
        addChildIntercept(referencedKeyConglomerateNum, info);
    }

    /* Add factories for *checking* existence of FK referenced primary-key or unique-index rows. */
    public void buildForeignKeyCheckWriteFactory(ReferencedKeyConstraintDescriptor cDescriptor) throws StandardException {
        ConstraintDescriptorList fks = cDescriptor.getForeignKeyConstraints(ConstraintDescriptor.ENABLED);
        if (fks.isEmpty()) {
            return;
        }
        ColumnDescriptorList backingIndexColDescriptors = cDescriptor.getColumnDescriptors();
        String parentTableName = cDescriptor.getTableDescriptor().getName();
        List<Long> backingIndexConglomIds = Lists.newArrayList();
        List<DDLMessage.FKConstraintInfo> fkConstraintInfos = Lists.newArrayList();
        for (ConstraintDescriptor fk : fks) {
            ForeignKeyConstraintDescriptor foreignKeyConstraint = (ForeignKeyConstraintDescriptor) fk;
            try {
                ConglomerateDescriptor backingIndexCd = foreignKeyConstraint.getIndexConglomerateDescriptor(null);
                backingIndexConglomIds.add(backingIndexCd.getConglomerateNumber());
                fkConstraintInfos.add(ProtoUtil.createFKConstraintInfo(foreignKeyConstraint));
            } catch (StandardException e) {
                throw new RuntimeException(e);
            }
        }
        addParentInterceptWriteFactory(parentTableName, backingIndexConglomIds,fkConstraintInfos);
    }



    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    //
    // derived convenience properties
    //
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    public boolean hasChildCheck() {
        return childInterceptWriteFactories !=null;
    }

    public boolean hasParentIntercept() {
        return parentInterceptWriteFactory != null;
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    //
    // getters/setters
    //
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    public List<ForeignKeyChildInterceptWriteFactory> getChildInterceptWriteFactories() {
        return Lists.newArrayList(childInterceptWriteFactories.values());
    }

    public ForeignKeyParentInterceptWriteFactory getParentInterceptWriteFactory() {
        return parentInterceptWriteFactory;
    }

}
