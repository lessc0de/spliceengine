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

package com.splicemachine.db.impl.sql.compile;

import com.splicemachine.db.iapi.sql.execute.ConstantAction;
import com.splicemachine.db.iapi.reference.SQLState;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.sql.dictionary.DataDictionary;
import com.splicemachine.db.iapi.sql.dictionary.SchemaDescriptor;
import com.splicemachine.db.iapi.sql.dictionary.SequenceDescriptor;

/**
 * A DropSequenceNode  represents a DROP SEQUENCE statement.
 */

public class DropSequenceNode extends DDLStatementNode {
    private TableName dropItem;

    /**
     * Initializer for a DropSequenceNode
     *
     * @param dropSequenceName The name of the sequence being dropped
     * @throws StandardException
     */
    public void init(Object dropSequenceName)
            throws StandardException {
        dropItem = (TableName) dropSequenceName;
        initAndCheck(dropItem);
    }

    public String statementToString() {
        return "DROP ".concat(dropItem.getTableName());
    }

    /**
     * Bind this DropSequenceNode.
     *
     * @throws StandardException Thrown on error
     */
    public void bindStatement() throws StandardException {
        DataDictionary dataDictionary = getDataDictionary();
        String sequenceName = getRelativeName();

        SequenceDescriptor seqDesc = null;
        SchemaDescriptor sd = getSchemaDescriptor();

        if (sd.getUUID() != null) {
            seqDesc = dataDictionary.getSequenceDescriptor
                    (sd, sequenceName);
        }
        if (seqDesc == null) {
            throw StandardException.newException(SQLState.LANG_OBJECT_DOES_NOT_EXIST, statementToString(), sequenceName);
        }

        // Statement is dependent on the SequenceDescriptor
        getCompilerContext().createDependency(seqDesc);
    }

    // inherit generate() method from DDLStatementNode


    /**
     * Create the Constant information that will drive the guts of Execution.
     *
     * @throws StandardException Thrown on failure
     */
    public ConstantAction makeConstantAction() throws StandardException {
        return getGenericConstantActionFactory().getDropSequenceConstantAction(getSchemaDescriptor(), getRelativeName());
	}

}
