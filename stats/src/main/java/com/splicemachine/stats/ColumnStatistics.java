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

package com.splicemachine.stats;

import com.splicemachine.stats.cardinality.CardinalityEstimator;
import com.splicemachine.stats.estimate.Distribution;
import com.splicemachine.stats.frequency.FrequentElements;

/**
 * Representation of Column-level statistics.
 *
 * Column statistics represent logical information about specific columns. Technically,
 * this is typed, but it is expected that there is a subinterface for primitive data types
 * in order to avoid auto-boxing where needed.
 *
 * @author Scott Fines
 *         Date: 2/23/15
 */
public interface ColumnStatistics<T> extends Mergeable<ColumnStatistics<T>> {

    /**
     * @return the total number of <em>non-null</em> elements in the data set.
     */
    long nonNullCount();

    /**
     * @return the cardinality of the Column values (e.g. the number of distinct
     *          elements) in the data set.
     */
    long cardinality();

    /**
     * @return the fraction of elements which are null relative to the count of records.
     */
    float nullFraction();

    /**
     * @return the number of rows which had a null value for this column
     */
    long nullCount();

    /**
     * @return the top {@code k} most frequently occurring values in this data set.
     *          {@code k}  is determined by configuration and collection.
     */
    FrequentElements<T> topK();

    /**
     * @return the minimum value for this column
     */
    T minValue();

    long minCount();
    /**
     * @return the maximum value for this column
     */
    T maxValue();

    /**
     * @return the width of the column (in bytes). In most cases, columns will be stored in byte arrays,
     * which in java require an integer index. Therefore, this column which is usually safe to store in an int.
     */
    int avgColumnWidth();

    /**
     * Make a copy of this Statistics object, for same thread management, etc.
     * @return a copy of this Statistics Object
     */
    ColumnStatistics<T> getClone();

    /**
     * @return a unique numeric identifier for this column (usually it's position from left to right in the table).
     * This value should be indexed from 0
     */
    int columnId();

    /**
     * @return a Distribution of values for the column
     */
    Distribution getDistribution();

    /**
     * @return total number of bytes for this column
     */
    long totalBytes();

    CardinalityEstimator getCardinalityEstimator();
}
