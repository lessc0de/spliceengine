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

import com.splicemachine.db.catalog.SequencePreallocator;

/**
 * <p>
 * Default Derby logic for determining how many values to pre-allocate for an
 * identity column or sequence.
 * </p>
 */
public  class   SequenceRange   implements  SequencePreallocator
{
    ///////////////////////////////////////////////////////////////////////////////////
    //
    // CONSTANTS
    //
    ///////////////////////////////////////////////////////////////////////////////////

    /**
     * Default number of values to pre-allocate. Other databases use a preallocation size
     * of 20 (see DERBY-4437). We boosted this to 100 in order to get better concurrency,
     * after fixing correctness problems in pre-allocation (see DERBY-5493).
     */
    private static final int DEFAULT_PREALLOCATION_COUNT = 100;

    ///////////////////////////////////////////////////////////////////////////////////
    //
    // STATE
    //
    ///////////////////////////////////////////////////////////////////////////////////

    private int _rangeSize;

    ///////////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTOR
    //
    ///////////////////////////////////////////////////////////////////////////////////

    /** <p>0-arg constructore needed to satisfy the SequencePreallocator contract.</p> */
    public  SequenceRange()
    {
        this( DEFAULT_PREALLOCATION_COUNT );
    }

    public  SequenceRange( int rangeSize )
    {
        if ( rangeSize <= 0 ) { rangeSize = DEFAULT_PREALLOCATION_COUNT; }
        
        _rangeSize = rangeSize;
    }

    ///////////////////////////////////////////////////////////////////////////////////
    //
    // SequencePreallocator BEHAVIOR
    //
    ///////////////////////////////////////////////////////////////////////////////////

    public  int nextRangeSize
        (
         String schemaName,
         String sequenceName
         )
    {
        return _rangeSize;
    }


}
