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

package com.splicemachine.stats.estimate;

import com.splicemachine.stats.LongColumnStatistics;
import com.splicemachine.stats.frequency.LongFrequencyEstimate;
import com.splicemachine.stats.frequency.LongFrequentElements;
import com.splicemachine.utils.ComparableComparator;

import java.util.Set;

/**
 * @author Scott Fines
 *         Date: 3/5/15
 */
public class UniformLongDistribution extends UniformDistribution<Long> implements LongDistribution {
    private final double a;

    public UniformLongDistribution(LongColumnStatistics columnStats) {
        super(columnStats, ComparableComparator.<Long>newComparator());
        /*
         * The CumulativeDistributionFunction(CDF) is a line from (min,minCount) to (max,nonNullCount),
         * but there are edges cases:
         *
         * 1. Empty distribution--when there are no elements in the distribution,
         * 2. Distribution consisting of a single element
         * 3. distribution containing multiple elements.
         *
         * In situation 1 and 2, the slope is undefinied (since you have 0/0). In shear correctness
         * terms, we have checks elsewhere in the function that will handle these scenarios gracefully without
         * recourse to using the linear interpolation. However, we put checks here for clarity and extra
         * safety in case the code changes in the future (and also so that we can put this note somewhere)
         */
        if(columnStats.nonNullCount()==0){
           //the distribution is empty, so the CumulativeDistribution Function is the 0 line
            this.a =0d;
        }else if(columnStats.max()==columnStats.min()){
            //the distribution is a single record, so the CDF is a constant function
            this.a = 0d;
        }else{
            double at=getAdjustedRowCount()-columnStats.minCount();
            /*
             * we up-cast to a double here explicitly so that we avoid potential
             * overflow issues with the subtraction of two values (e.g. if max()==Long.MAX_VALUE
             * and min() ==Long.MIN_VALUE).
             */
            at/=((double)columnStats.max()-columnStats.min());

            this.a=at;
        }
    }

    @Override
    protected long estimateRange(Long start, Long stop, boolean includeStart, boolean includeStop, boolean isMin) {
        return rangeSelectivity(start,stop,includeStart,includeStop,isMin);
    }

    @Override
    public long selectivity(long value) {
        LongColumnStatistics scs = (LongColumnStatistics)columnStats;
        if(value<scs.min()||value>scs.max()) return  0l;
        if(value==scs.min()) return scs.minCount();

        LongFrequentElements fes = (LongFrequentElements)scs.topK();
        LongFrequencyEstimate longFrequencyEstimate = fes.countEqual(value);
        if(longFrequencyEstimate.count()>0) return longFrequencyEstimate.count();
        return uniformEstimate();
    }

    @Override
    public long selectivityBefore(long stop, boolean includeStop) {
        LongColumnStatistics scs = (LongColumnStatistics)columnStats;
        if(stop<scs.min()||(!includeStop && stop==scs.min())) return 0l;

        return rangeSelectivity(scs.min(),stop,true,includeStop);
    }

    @Override
    public long selectivityAfter(long start, boolean includeStart) {
        LongColumnStatistics scs = (LongColumnStatistics)columnStats;
        if(start>scs.max()||(!includeStart && start==scs.max())) return 0l;

        return rangeSelectivity(start,scs.max(),includeStart,true);
    }

    @Override
    public long rangeSelectivity(long start, long stop, boolean includeStart, boolean includeStop) {
        LongColumnStatistics scs = (LongColumnStatistics)columnStats;
        if(start==stop &&(!includeStart || !includeStop)) return 0l; //asking for an empty range
        long min = scs.min();
        if(stop<min||(!includeStop && stop==min)) return 0l;
        else if(includeStop && stop==min) return selectivity(min);

        long max = scs.max();
        if(start>max||(!includeStart && start==max)) return 0l;
        else if(includeStart && start==max) return selectivity(max);

        /*
         * We now have a range [a,b) which definitely overlaps, but it might not be wholly contained. Adjust
         * it to fit wholly within the data range
         */
        boolean isMin = false;
        if(start<=min){
            includeStart=includeStart||start<min;
            start = min;
            isMin = true;
        }
        if(stop>max) {
            stop = max;
            includeStop= true;
        }
        return rangeSelectivity(start,stop,includeStart,includeStop,isMin);
    }

    @Override public long min(){ return ((LongColumnStatistics)columnStats).min(); }
    @Override public long max(){ return ((LongColumnStatistics)columnStats).max(); }
    @Override public long totalCount(){ return columnStats.nonNullCount(); }
    @Override public Long minValue(){ return min(); }
    @Override public Long maxValue(){ return max(); }
    @Override public long minCount(){ return columnStats.minCount(); }
    public long cardinality(){ return columnStats.cardinality(); }

    public long rangeCardinality(long start,long stop,boolean includeStart,boolean includeStop){
        LongColumnStatistics scs = (LongColumnStatistics)columnStats;
        if(start==stop &&(!includeStart || !includeStop)) return 0l; //asking for an empty range
        long min = scs.min();
        if(stop<min||(!includeStop && stop==min)) return 0l;
        else if(includeStop && stop==min) return selectivity(min);

        long max = scs.max();
        if(start>max||(!includeStart && start==max)) return 0l;
        else if(includeStart && start==max) return selectivity(max);

        /*
         * We now have a range [a,b) which definitely overlaps, but it might not be wholly contained. Adjust
         * it to fit wholly within the data range
         */
        if(start<=min){
            includeStart=includeStart||start<min;
            start = min;
        }
        if(stop>max) {
            stop = max;
            includeStop= true;
        }
        long c = stop-start;
        if(!includeStart) c--;
        if(includeStop) c++;
        return c;
    }

    /* ****************************************************************************************************************/
    /*private helper methods*/
    private long rangeSelectivity(long start, long stop, boolean includeStart, boolean includeStop, boolean isMin) {
        /*
         * distance is always positive, and we've checked before to ensure that stop>start; unfortunately,
         * we could overflow with long subtraction here and still end up with a negative distance. We
         * avoid this by upcasting to a double explicitly before proceeding.
         */
        double distance = (double)stop-start;
        double baseEstimate = a*(distance);

        //adjust using Frequent Elements
        LongFrequentElements sfe = (LongFrequentElements)columnStats.topK();
        //if we are the min value, don't include the start key in frequent elements
        boolean includeStartFreqs=includeStart && !isMin;
        Set<LongFrequencyEstimate> longFrequencyEstimates = sfe.frequentBetween(start, stop,includeStartFreqs, includeStop);
        long l=uniformRangeCount(includeStartFreqs,includeStop,baseEstimate,longFrequencyEstimates);
        if(isMin&&includeStart)
            l+=minCount();
        return l;
    }
}
