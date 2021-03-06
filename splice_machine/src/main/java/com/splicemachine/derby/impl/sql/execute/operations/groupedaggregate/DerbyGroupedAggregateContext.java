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

package com.splicemachine.derby.impl.sql.execute.operations.groupedaggregate;

import com.splicemachine.derby.iapi.sql.execute.SpliceOperationContext;
import com.splicemachine.derby.impl.sql.execute.operations.iapi.AggregateContext;
import com.splicemachine.derby.impl.sql.execute.operations.framework.SpliceGenericAggregator;
import com.splicemachine.db.iapi.error.SQLWarningFactory;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.services.io.FormatableArrayHolder;
import com.splicemachine.db.iapi.sql.Activation;
import com.splicemachine.db.iapi.store.access.ColumnOrdering;
import com.splicemachine.db.impl.sql.GenericStorablePreparedStatement;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.sparkproject.guava.collect.Lists;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;

/**
 * @author Scott Fines
 * Created on: 11/4/13
 */
public class DerbyGroupedAggregateContext implements GroupedAggregateContext {
    private Activation activation;
    private int orderingItem;
    private int[] groupingKeys;
    private boolean[] groupingKeyOrder;
    private int[] nonGroupedUniqueColumns;
    private int numDistinctAggs;

    public DerbyGroupedAggregateContext() {
    }

    public DerbyGroupedAggregateContext(int orderingItem) {
        this.orderingItem = orderingItem;
    }

    @Override
    public void init(SpliceOperationContext context,
                     AggregateContext genericAggregateContext) throws StandardException {
        this.activation = context.getActivation();

        GenericStorablePreparedStatement statement = context.getPreparedStatement();
        ColumnOrdering[] orderings = (ColumnOrdering[])
                ((FormatableArrayHolder) (statement.getSavedObject(orderingItem))).getArray(ColumnOrdering.class);

        int[] allKeyedColumns = new int[orderings.length];
        boolean[] allSortOrders = new boolean[orderings.length];
        int pos=0;
        for(ColumnOrdering order:orderings){
            allKeyedColumns[pos] = order.getColumnId();
            allSortOrders[pos] = order.getIsAscending();
            pos++;
        }

        List<Integer> nonUniqueColumns = Lists.newArrayListWithExpectedSize(0);
        SpliceGenericAggregator[] aggregates = genericAggregateContext.getAggregators();
        for(SpliceGenericAggregator aggregate: aggregates){
            if(aggregate.isDistinct()){
                int inputColNum = aggregate.getAggregatorInfo().getInputColNum();
                if(!keysContain(allKeyedColumns,inputColNum)){
                   nonUniqueColumns.add(inputColNum);
               }
               numDistinctAggs++;
            }
        }

        if(numDistinctAggs>0){
            groupingKeys = new int[allKeyedColumns.length-1];
            System.arraycopy(allKeyedColumns,0,groupingKeys,0,groupingKeys.length);
            groupingKeyOrder = new boolean[allSortOrders.length-1];
            System.arraycopy(allSortOrders,0,groupingKeyOrder,0,groupingKeyOrder.length);

            nonUniqueColumns.add(allKeyedColumns[allKeyedColumns.length-1]);
            nonGroupedUniqueColumns = new int[nonUniqueColumns.size()];
            pos=0;
            for(Integer nonUniqueColumn:nonUniqueColumns){
                nonGroupedUniqueColumns[pos] = nonUniqueColumn;
                pos++;
            }
        }else{
            groupingKeys = allKeyedColumns;
            groupingKeyOrder = allSortOrders;
            nonGroupedUniqueColumns = new int[]{};
        }
    }

    private boolean keysContain(int[] keyColumns, int inputColNum) {
        for(int keyColumn:keyColumns){
            if(keyColumn==inputColNum)
                return true;
        }
        return false;
    }

    @Override
    @SuppressFBWarnings(value = "EI_EXPOSE_REP",justification = "Intentional")
    public int[] getGroupingKeys() {
        return groupingKeys;
    }

    @Override
    @SuppressFBWarnings(value = "EI_EXPOSE_REP",justification = "Intentional")
    public boolean[] getGroupingKeyOrder() {
        return groupingKeyOrder;
    }

    @Override
    @SuppressFBWarnings(value = "EI_EXPOSE_REP",justification = "Intentional")
    public int[] getNonGroupedUniqueColumns() {
        return nonGroupedUniqueColumns;
    }

    @Override
    public int getNumDistinctAggregates() {
        return numDistinctAggs;
    }

    @Override
    public void addWarning(String warningState) throws StandardException {
        activation.addWarning(SQLWarningFactory.newSQLWarning(warningState));
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(orderingItem);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        orderingItem = in.readInt();
    }
}
