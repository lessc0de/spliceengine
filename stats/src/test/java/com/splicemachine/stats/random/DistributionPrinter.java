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

package com.splicemachine.stats.random;

import com.carrotsearch.hppc.IntLongOpenHashMap;
import org.sparkproject.guava.base.Strings;

import java.util.Random;

/**
 * @author Scott Fines
 *         Date: 12/2/14
 */
public class DistributionPrinter {

    public static void main(String...args) throws Exception{
        int numElements = 10000;
//        RandomDistribution dist = new ParetoDistribution(new UniformDistribution(new Random(0l),1,3));
//        RandomDistribution dist = new ParetoDistribution(new UniformDistribution(new Random()),.1d,.9d);
//        RandomDistribution dist = new ExponentialDistribution(new UniformDistribution(new Random()));
        RandomGenerator dist = new GaussianGenerator(new UniformGenerator(new Random(0l)));
//        boolean useLogScale=true;
        boolean useLogScale=false;
        runTest(numElements, dist,useLogScale);
    }

    private static void runTest(int numElements, RandomGenerator dist,boolean useLogScale) {
        IntLongOpenHashMap data = new IntLongOpenHashMap();
        int scale = 10;
        int maxKey = 0;
        int minKey = 0;
        double mean = 0;
        double var = 0;
        for(int i=0;i<numElements;i++){
            double d = dist.nextDouble()*scale;
            int key = (int)(Math.floor(d));
            data.putOrAdd(key,1l,1l);
            if(key>maxKey){
                maxKey = Math.abs(key);
            }
            if(key<minKey){
                minKey = key;
            }
            double oldMean = mean;
            mean += (d-mean)/(i+1);
            var += (d-oldMean)*(d-mean);
        }

        printHistogram(numElements, data, maxKey, minKey,useLogScale);

        System.out.println("----------------------");
        System.out.printf("Mean: %f%n", mean);
        System.out.printf("Std Dev: %f%n",Math.sqrt(var/numElements));
    }

    private static void printHistogram(int numElements, IntLongOpenHashMap data, int maxKey, int minKey,boolean useLogScale) {
        int histScale = numElements<100? 1: numElements<1000? 10: numElements<=10000? 10: 1000;
//        int histScale=1;
        long[] sortedCounts = new long[maxKey-minKey+1];
        for(int i=minKey;i<=maxKey;i++){
            sortedCounts[i-minKey] = data.get(i);
        }
        for(int i=0;i<sortedCounts.length;i++){
            long length = sortedCounts[i];
            int rep = useLogScale? (int)Math.log(length): (int)length/histScale;
            String e = useLogScale? String.format("%5f", Math.log(i + minKey)): String.format("%5d", i + minKey);
            if((useLogScale && length>0)||!useLogScale)
                System.out.printf("%s(%d)\t%s%n", e,length, Strings.repeat(".", rep));
        }
    }
}
