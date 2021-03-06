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

import com.carrotsearch.hppc.ShortArrayList;
import com.splicemachine.stats.CombinedShortColumnStatistics;
import com.splicemachine.stats.ShortColumnStatistics;
import com.splicemachine.stats.cardinality.CardinalityEstimators;
import com.splicemachine.stats.collector.ColumnStatsCollectors;
import com.splicemachine.stats.collector.ShortColumnStatsCollector;
import com.splicemachine.stats.frequency.FrequencyCounters;
import com.splicemachine.stats.frequency.ShortFrequencyCounter;
import com.splicemachine.stats.frequency.ShortFrequentElements;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

/**
 * @author Scott Fines
 *         Date: 6/25/15
 */
public class UniformShortDistributionTest{

    @Test
    public void testSelectivityRemainsBounded() throws Exception{
        /*
         * The idea here is to ensure that the selectivity estimates that we provide don't violate
         * the invariants of falling within the range [0,totalCount()).
         */
        ShortColumnStatsCollector col = ColumnStatsCollectors.shortCollector(0,(short)14,(short)5);
        ShortArrayList values = new ShortArrayList(100);
        for(short i=1;i>0 &&i<Short.MAX_VALUE;i<<=1){

            short v = i;
            col.update(v);
            values.add(v);
            v = (short)-v;
            col.update(v);
            values.add(v);

        }

        values.add((short)0);
        values.add(Short.MAX_VALUE);
        values.add(Short.MIN_VALUE);

        ShortDistribution distribution=(ShortDistribution)col.build().getDistribution();
        short[] v = values.toArray();
        Arrays.sort(v);
        for(int i=0;i<v.length;i++){
            short mi = v[i];
            long sel = distribution.selectivity(mi);

            Assert.assertTrue("negative selectivity!",sel>=0);
            Assert.assertTrue("overlarge selectivity!",sel<=v.length);
            Assert.assertTrue("overlarge selectivity!",sel<=distribution.totalCount());

            for(int j=i+1;j<v.length;j++){
                short ma = v[j];

                long rs=distribution.rangeSelectivity(mi,ma,true,true);
                Assert.assertTrue("negative selectivity: mi=<"+mi+">, ma=<"+ma+">!rs="+rs,rs>=0);
                Assert.assertTrue("overlarge selectivity: mi=<"+mi+">, ma=<"+ma+">!:rs="+rs,rs<=v.length);
                Assert.assertTrue("overlarge selectivity!",rs<=distribution.totalCount());

                rs=distribution.rangeSelectivity(mi,ma,true,false);
                Assert.assertTrue("negative selectivity: mi=<"+mi+">, ma=<"+ma+">!rs="+rs,rs>=0);
                Assert.assertTrue("overlarge selectivity: mi=<"+mi+">, ma=<"+ma+">!:rs="+rs,rs<=v.length);
                Assert.assertTrue("overlarge selectivity!",rs<=distribution.totalCount());

                rs=distribution.rangeSelectivity(mi,ma,false,true);
                Assert.assertTrue("negative selectivity: mi=<"+mi+">, ma=<"+ma+">!rs="+rs,rs>=0);
                Assert.assertTrue("overlarge selectivity: mi=<"+mi+">, ma=<"+ma+">!:rs="+rs,rs<=v.length);
                Assert.assertTrue("overlarge selectivity!",rs<=distribution.totalCount());

                rs=distribution.rangeSelectivity(mi,ma,false,false);
                Assert.assertTrue("negative selectivity: mi=<"+mi+">, ma=<"+ma+">!rs="+rs,rs>=0);
                Assert.assertTrue("overlarge selectivity: mi=<"+mi+">, ma=<"+ma+">!:rs="+rs,rs<=v.length);
                Assert.assertTrue("overlarge selectivity!",rs<=distribution.totalCount());
            }
        }
    }
    @Test
    public void testGetPositiveCountForNegativeStartValues() throws Exception{
        ShortColumnStatsCollector col =ColumnStatsCollectors.shortCollector(0,(short)14,(short)5);
        for(int i=0;i<14;i++){
            col.update((short)0);
            col.update((short)1);
            col.update((short)-1);
            col.update(Short.MIN_VALUE);
            col.update(Short.MAX_VALUE);
        }

        ShortColumnStatistics lcs = col.build();
        ShortDistribution distribution = new UniformShortDistribution(lcs);

        long l=distribution.rangeSelectivity(Short.MIN_VALUE,(short)0,false,false);
        Assert.assertTrue("Negative Selectivity!",l>=0);
        Assert.assertEquals("Incorrect selectivity!",14,l);

        l=distribution.rangeSelectivity(Short.MIN_VALUE,(short)0,true,false);
        Assert.assertTrue("Negative Selectivity!",l>=0);
        Assert.assertEquals("Incorrect selectivity!",28,l);

        l=distribution.rangeSelectivity(Short.MIN_VALUE,(short)0,true,true);
        Assert.assertTrue("Negative Selectivity!",l>=0);
        Assert.assertEquals("Incorrect selectivity!",3*14l,l);
    }

    @Test
    public void distributionWorksWithFrequentElements() throws Exception {
 
    	ShortFrequencyCounter counter = FrequencyCounters.shortCounter((short)4);

        // Values repeated on purpose
        counter.update((short)101);
        counter.update((short)102);
        counter.update((short)102);
        counter.update((short)103);
        counter.update((short)103);
        counter.update((short)103);
        counter.update((short)104);
        counter.update((short)104);
        counter.update((short)104);
        counter.update((short)104);
        
		ShortFrequentElements fe = (ShortFrequentElements)counter.frequentElements(4);

        ShortColumnStatistics colStats = new CombinedShortColumnStatistics(0,
            CardinalityEstimators.hyperLogLogShort(4),
            fe,
            (short)101,
            (short)104,
            200,
            12,
            0,
            2);

        UniformShortDistribution dist = new UniformShortDistribution(colStats);

        Assert.assertEquals(2, dist.selectivity((short)101)); // return min of 2, not actual 1
        Assert.assertEquals(2, dist.selectivity((short)102));
        Assert.assertEquals(3, dist.selectivity((short)103));
        Assert.assertEquals(4, dist.selectivity((short)104));
        Assert.assertEquals(0, dist.selectivity((short)105));
    }
	
    @Test
    public void testDistributionWorksWithSingleElement() throws Exception{
        //the test is to make sure that we can create the entity without it breaking
        ShortColumnStatistics scs = new CombinedShortColumnStatistics(0,
                CardinalityEstimators.hyperLogLogShort(4),
                FrequencyCounters.shortCounter((short)4).frequentElements(4),
                (short)1,
                (short)1,
                2,
                12,
                0,
                3);

        UniformShortDistribution dist=new UniformShortDistribution(scs);
        /*
         * We need to make sure of the following things:
         *
         * 1. values == min or max return the correct count
         * 2. Values != min return 0
         * 3. Range estimates which include the min return minCount
         * 4. Range estimates which do not include the min return 0
         */
        Assert.assertEquals(scs.minCount(),dist.selectivity(scs.min()));
        Assert.assertEquals(0l,dist.selectivity((short)(scs.min()+1)));

        Assert.assertEquals(scs.minCount(),dist.rangeSelectivity(scs.min(),(short)(scs.min()+1),true,true));
        Assert.assertEquals(0,dist.rangeSelectivity(scs.min(),(short)(scs.min()+1),false,true));
    }

    @Test
    public void emptyDistributionReturnsZeroForAllEstimates() throws Exception{
        //the test is to make sure that we can create the entity without it breaking
        ShortColumnStatistics scs = new CombinedShortColumnStatistics(0,
                CardinalityEstimators.hyperLogLogShort(4),
                FrequencyCounters.shortCounter((short)4).frequentElements(4),
                (short)0,
                (short)0,
                0,
                0,
                0,
                0);

        UniformShortDistribution dist=new UniformShortDistribution(scs);
        /*
         * We need to make sure we return 0 in the following scenarios:
         *
         * 1. values == scs.min()
         * 2. Values != min return 0
         * 3. Range estimates which include scs.min()
         * 4. Range estimates which do not include the min return 0
         */
        Assert.assertEquals(0,dist.selectivity(scs.min()));
        Assert.assertEquals(0l,dist.selectivity((short)(scs.min()+1)));

        Assert.assertEquals(0,dist.rangeSelectivity(scs.min(),(short)(scs.min()+1),true,true));
        Assert.assertEquals(0,dist.rangeSelectivity(scs.min(),(short)(scs.min()+1),false,true));
    }
}