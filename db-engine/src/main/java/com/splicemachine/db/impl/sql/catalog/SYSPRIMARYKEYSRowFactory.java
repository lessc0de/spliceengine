/*
 * Apache Derby is a subproject of the Apache DB project, and is licensed under
 * the Apache License, Version 2.0 (the "License"); you may not use these files
 * except in compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Splice Machine, Inc. has modified this file.
 *
 * All Splice Machine modifications are Copyright 2012 - 2016 Splice Machine, Inc.,
 * and are licensed to you under the License; you may not use this file except in
 * compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */

package com.splicemachine.db.impl.sql.catalog;

import com.splicemachine.db.catalog.UUID;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.services.sanity.SanityManager;
import com.splicemachine.db.iapi.services.uuid.UUIDFactory;
import com.splicemachine.db.iapi.sql.dictionary.*;
import com.splicemachine.db.iapi.sql.execute.ExecRow;
import com.splicemachine.db.iapi.sql.execute.ExecutionFactory;
import com.splicemachine.db.iapi.types.DataValueDescriptor;
import com.splicemachine.db.iapi.types.DataValueFactory;
import com.splicemachine.db.iapi.types.SQLChar;
import com.splicemachine.db.impl.services.uuid.BasicUUID;

/**
 * @author Scott Fines
 * Created on: 3/1/13
 */
public class SYSPRIMARYKEYSRowFactory extends CatalogRowFactory {

    private static final String TABLENAME_STRING = "SYSPRIMARYKEYS";

    protected static final int SYSPRIMARYKEYS_COLUMN_COUNT=2;

    public static final int SYSPRIMARYKEYS_INDEX1_ID = 0;

    /*Column position numbers */
    public static final int SYSPRIMARYKEYS_CONSTRAINTID=1;
    public static final int SYSPRIMARYKEYS_CONGLOMERATEID=2;

    private static final boolean[] uniqueness = null;

    private static final int[][] indexColumnPositions = {{SYSPRIMARYKEYS_CONSTRAINTID}};

    private static final String[] uuids = new String[]{
            "f48ad515-013d-35d6-f400-6915f6177d2f", //catalog
            "f48ad516-013d-35d6-f400-6915f6177d2f", //heap
            "f48ad516-013d-35d6-f400-6915f6177d2f", //INDEX_1
    };

    public SYSPRIMARYKEYSRowFactory(UUIDFactory uuidf, ExecutionFactory ef, DataValueFactory dvf) {
        super(uuidf, ef, dvf);
        initInfo(SYSPRIMARYKEYS_COLUMN_COUNT,TABLENAME_STRING,indexColumnPositions,uniqueness,uuids);
    }

    @Override
    public ExecRow makeRow(TupleDescriptor td, TupleDescriptor parent) throws StandardException {
        UUID oid;
        String constraintOid = null;
        String conglomerateId = null;
        if(td!=null){
            KeyConstraintDescriptor constraint = (KeyConstraintDescriptor)td;

            oid = constraint.getUUID();
            constraintOid = oid.toString();

            //find the Table conglomerate UUID
            ConglomerateDescriptorList cdl = constraint.getTableDescriptor().getConglomerateDescriptorList();
            for(int index=0;index<cdl.size();index++){
                ConglomerateDescriptor cd = (ConglomerateDescriptor) cdl.get(index);
                TableDescriptor tableDescriptor = constraint.getTableDescriptor();
                if(tableDescriptor.getHeapConglomerateId()==cd.getConglomerateNumber()){
                    conglomerateId = cd.getUUID().toString();
                    break;
                }
            }
        }

        ExecRow row = getExecutionFactory().getValueRow(SYSPRIMARYKEYS_COLUMN_COUNT);
        row.setColumn(SYSPRIMARYKEYS_CONSTRAINTID,new SQLChar(constraintOid));
        row.setColumn(SYSPRIMARYKEYS_CONGLOMERATEID,new SQLChar(conglomerateId));

        return row;
    }

    @Override
    public TupleDescriptor buildDescriptor(ExecRow row,
                                           TupleDescriptor parentTuple,
                                           DataDictionary dataDictionary)
            throws StandardException {
        if(SanityManager.DEBUG){
            SanityManager.ASSERT(
                    row.nColumns()==SYSPRIMARYKEYS_COLUMN_COUNT,
                    "Wrong number of columns for a SYSPRIMARYKEYS row");
        }

        DataDescriptorGenerator ddg = dataDictionary.getDataDescriptorGenerator();

        /*
         * First column is a constraint UUID
         * Second column is the conglomerate for the table with the PK constraint
         */
        DataValueDescriptor col = row.getColumn(SYSPRIMARYKEYS_CONSTRAINTID);
        String constraintUUIDString = col.getString();
        UUID constraintUUID = getUUIDFactory().recreateUUID(constraintUUIDString);

        col = row.getColumn(SYSPRIMARYKEYS_CONGLOMERATEID);
        String conglomerateUUIDString = col.getString();
        UUID conglomerateUUID = getUUIDFactory().recreateUUID(conglomerateUUIDString);

        return new SubKeyConstraintDescriptor(constraintUUID,conglomerateUUID);
    }

    @Override
    public SystemColumn[] buildColumnList() throws StandardException {
        return new SystemColumn[]{
                SystemColumnImpl.getUUIDColumn("CONSTRAINTID", false),
                SystemColumnImpl.getUUIDColumn("CONGLOMERATEID",false)
        };
    }

    public static void main(String... args) throws Exception{

        BasicUUID one = new BasicUUID(50369424,System.currentTimeMillis(),136724499);

        BasicUUID two = new BasicUUID(50369424,1362416594897l,-1607811053);

        System.out.printf("one=%s,two=%s,one.toString().equals(two.toString())=%s%n",
                one,two,one.toString().equals(two.toString()));

    }
}
