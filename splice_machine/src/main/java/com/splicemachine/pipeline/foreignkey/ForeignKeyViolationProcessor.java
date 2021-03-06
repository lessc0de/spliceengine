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

import com.splicemachine.ddl.DDLMessage.*;
import com.splicemachine.pipeline.api.Code;
import com.splicemachine.pipeline.api.PipelineExceptionFactory;
import com.splicemachine.pipeline.constraint.ConstraintContext;
import com.splicemachine.pipeline.client.WriteResult;
import com.splicemachine.pipeline.constraint.ForeignKeyViolation;
import com.splicemachine.pipeline.context.WriteContext;
import com.splicemachine.primitives.Bytes;

/**
 * We intercept writes on either the parent or child table and check for the existence of referenced or referring
 * rows on the child or parent table(s).  When those writes fail the remote *CheckWriteHandler returns a failure
 * to us in the form of an exception.  This class is used by the intercept write handlers (*InterceptWriteHandler) to
 * take the remote exception and translate it into a user-friendly foreign key violation error message.
 */
class ForeignKeyViolationProcessor {

    private final FkConstraintContextProvider fkConstraintContextProvider;
    private final PipelineExceptionFactory exceptionFactory;

    ForeignKeyViolationProcessor(FkConstraintContextProvider fkConstraintContextProvider,
                                 PipelineExceptionFactory exceptionFactory) {
        this.fkConstraintContextProvider = fkConstraintContextProvider;
        this.exceptionFactory = exceptionFactory;
    }

    /**
     * This code looks fragile but it is validated by every single FK IT test method. Breakages in this method would
     * result in all FK ITs failing. Still, it would be nice if would could simplify this.  DB-2952 is for simplifying
     * how error details are passed between FK CheckWriteHandlers and FK InterceptWriteHandlers.
     */
    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    public void failWrite(Exception originalException, WriteContext ctx) {
        Throwable t =exceptionFactory.processPipelineException(originalException);
        if(t instanceof ForeignKeyViolation){
            doFail(ctx,(ForeignKeyViolation)t);
        }
    }

    private void doFail(WriteContext ctx, ForeignKeyViolation cause) {
        String hexEncodedFailedRowKey = cause.getContext().getMessages()[0];
        byte[] failedRowKey = Bytes.fromHex(hexEncodedFailedRowKey);
        ConstraintContext constraintContext = fkConstraintContextProvider.get(cause);
        ctx.result(failedRowKey, new WriteResult(Code.FOREIGN_KEY_VIOLATION, constraintContext));
    }


    /**
     * For the FK violation error message we need: table name, constraint name, and fk columns.  There is a
     * factory method in ConstraintContext for creating a ConstraintContext with just this information from a
     * FKConstraintInfo.  A slight complication is that how we get a FKConstraintInfo depends on where the
     * failure happened, etc. Thus the abstraction below.
     */
    interface FkConstraintContextProvider {
        ConstraintContext get(ForeignKeyViolation cause);
    }

    static class ChildFkConstraintContextProvider implements FkConstraintContextProvider {
        private FKConstraintInfo fkConstraintInfo;

        public ChildFkConstraintContextProvider(FKConstraintInfo fkConstraintInfo) {
            this.fkConstraintInfo = fkConstraintInfo;
        }

        @Override
        public ConstraintContext get(ForeignKeyViolation cause) {
            // I'm on the child and thus have a local reference to the FK constraint descriptor.
            //
            // Error message looks like: INSERT on table 'C' caused a violation of foreign key constraint 'FK_1' for key (5).
            //
            return ConstraintContext.foreignKey(fkConstraintInfo);
        }
    }

    static class ParentFkConstraintContextProvider implements FkConstraintContextProvider {

        private String parentTableName;

        ParentFkConstraintContextProvider(String parentTableName) {
            this.parentTableName = parentTableName;
        }

        @Override
        public ConstraintContext get(ForeignKeyViolation cause) {
            // I'm on the parent table. The correct error message in this case should have the
            // FK constraint name and keys from the child (only it knows which FK constraint actually failed)
            // but the PARENT table name.
            //
            // Error message looks like: DELETE on table 'P' caused a violation of foreign key constraint 'FK_1' for key (5).
            //
            return cause.getContext()
                    .withoutMessage(0)                      // Remove the rowKey
                    .withMessage(1, parentTableName);       // Add correct table name
        }
    }

}
