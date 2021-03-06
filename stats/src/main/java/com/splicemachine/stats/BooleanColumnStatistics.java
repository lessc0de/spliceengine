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

import com.splicemachine.encoding.Encoder;
import com.splicemachine.stats.cardinality.CardinalityEstimator;
import com.splicemachine.stats.estimate.BooleanDistribution;
import com.splicemachine.stats.estimate.Distribution;
import com.splicemachine.stats.frequency.BooleanFrequencyEstimate;
import com.splicemachine.stats.frequency.BooleanFrequentElements;
import com.splicemachine.stats.frequency.FrequencyCounters;
import com.splicemachine.stats.frequency.FrequentElements;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Scott Fines
 *         Date: 2/23/15
 */
public class BooleanColumnStatistics extends BaseColumnStatistics<Boolean> {
    private BooleanFrequentElements frequentElements;
    private Distribution<Boolean> distribution;

    public BooleanColumnStatistics( int columnId,
                                    BooleanFrequentElements frequentElements,
                                    long totalBytes,
                                    long totalCount,
                                    long nullCount) {
        super(columnId,totalBytes,totalCount,nullCount);
        this.frequentElements = frequentElements;
    }

    @Override public long cardinality() {
        long c = 0;
        if(frequentElements.equalsTrue().count()>0) c++;
        if(frequentElements.equalsFalse().count()>0) c++;
        return c;
    }

    @Override public FrequentElements<Boolean> topK() { return frequentElements; }
    @Override
    public Boolean minValue() {
        if(frequentElements.equalsTrue().count()>0||frequentElements.totalFrequentElements()<=0) {
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    @Override public Boolean maxValue() {
        if(frequentElements.equalsFalse().count()>0) return Boolean.FALSE;
        return Boolean.TRUE;
    }

    public BooleanFrequencyEstimate trueCount(){
        return frequentElements.equalsTrue();
    }

    public BooleanFrequencyEstimate falseCount(){
        return frequentElements.equalsFalse();
    }

    @Override
    public long minCount(){
        if(frequentElements.equalsTrue().count()>0 || frequentElements.totalFrequentElements()<=0)
            return frequentElements.equalsTrue().count();
        return frequentElements.equalsFalse().count();
    }

    @Override
    public ColumnStatistics<Boolean> getClone() {
        return new BooleanColumnStatistics(columnId,frequentElements.getClone(),totalBytes,totalCount,nullCount);
    }

    @Override
    public Distribution<Boolean> getDistribution() {
        if(distribution==null){
           distribution = new BooleanDistribution(nullCount,this.frequentElements);
        }
        return distribution;
    }

    @Override
    public CardinalityEstimator getCardinalityEstimator() {
        throw new RuntimeException("getCardinalityEstimator not implemented for BooleanColumnStatistics");
    }
    @Override
    public ColumnStatistics<Boolean> merge(ColumnStatistics<Boolean> other) {

        frequentElements = (BooleanFrequentElements)frequentElements.merge(other.topK());
        totalBytes+=other.totalBytes();
        totalCount+=other.nonNullCount() + other.nullCount();
        nullCount+=other.nullCount();
        return this;
    }

    public static Encoder<BooleanColumnStatistics> encoder(){
        return EncDec.INSTANCE;
    }

    static class EncDec implements Encoder<BooleanColumnStatistics> {
        public static final EncDec INSTANCE = new EncDec();

        @Override
        public void encode(BooleanColumnStatistics item,DataOutput encoder) throws IOException {
            encoder.writeInt(item.columnId);
            encoder.writeLong(item.totalBytes);
            encoder.writeLong(item.totalCount);
            encoder.writeLong(item.nullCount);
            FrequencyCounters.booleanEncoder().encode(item.frequentElements,encoder);
        }

        @Override
        public BooleanColumnStatistics decode(DataInput decoder) throws IOException {
            int columnId = decoder.readInt();
            long totalBytes = decoder.readLong();
            long totalCount = decoder.readLong();
            long nullCount = decoder.readLong();
            BooleanFrequentElements frequentElements = FrequencyCounters.booleanEncoder().decode(decoder);
            return new BooleanColumnStatistics(columnId,frequentElements,totalBytes,totalCount,nullCount);
        }
    }
}

