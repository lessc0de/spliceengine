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

package com.splicemachine.stats.histogram;

import com.carrotsearch.hppc.IntDoubleOpenHashMap;
import com.splicemachine.hash.Hash32;
import com.splicemachine.hash.HashFunctions;
import com.splicemachine.primitives.MoreArrays;
import com.splicemachine.stats.IntUpdateable;
import com.splicemachine.stats.order.IntMinMaxCollector;

/**
 * A streaming version of the Haar wavelet transform.
 *
 * The Haar wavelet transform is a relatively simple wavelet transform which
 * represents local changes in data using a combination of high- and low-pass filters.
 * For more information on the Haar transform, consult Wikipedia
 * (<a href="http://en.wikipedia.org/wiki/Haar_wavelet"/>) or one of many texts on
 * wavelet transformations (such as "An Introduction to Wavelet Analysis"
 * by David F. Walnut, ISBN-13: 978-0817639624).
 *
 * <h1>Implementation Details</h1>
 * This implementation is based heavily on the algorithm described in
 * "Fast approximate Wavelet Tracking on Streams", by Cormode et al
 * (<a href="http://dimacs.rutgers.edu/~graham/pubs/papers/gc-sketch.pdf" />), with
 * several minor variations.
 *
 * The main idea is to divide up the domain of possible values {@code [-N/2,N/2)} into
 * <em>dyadic intervals</em>
 * (<a href="http://en.wikipedia.org/wiki/Interval_%28mathematics%29#Dyadic_intervals"/>),
 * and compute Haar transform coefficients for each interval in a streaming manner.
 *
 * Mathematically, there are {@code lg(N)} different ways to divide {@code [-N/2,N/2)}
 * into dyadic intervals; These are held together in a tree hierarchy, where the dyadic
 * interval covered at each node of the tree is the union of the dyadic intervals
 * directly below the node. At each node, we are able to associate a specific
 * coefficient of the Haar Transform, which we can update individually.
 *
 * The beauty of this approach is that we need to update only {@code lg(N)} coefficients
 * for each element added; further, to reconstruct the original frequency, we need
 * only use the {@code lg(N)} involved coefficients--all others are unnecessary.
 *
 * <h3>Energy and Transform compression</h3>
 * In a perfect, lossless, world, the Haar Transform would have exactly as many coefficients
 * as there are elements in the domain--{@code N}. This stinks, because it requires
 * as much storage as the original data set itself.
 *
 * However, we can observe that many of these coefficients contribute very
 * little to the overall frequency count in practive. If we replace these
 * coefficients with 0, then we have reduced our storage requirement in exchange
 * for a small amount of error in the resulting estimates. This allows us to perform
 * lossy compression of the transform.
 *
 * Cormode et al's approach is to identify coefficients with small <em>energy</em>(
 * where energy is a representation of the amount the coefficient can possibly contribute
 * to the overall frequency). These coefficients can be eliminated without introducing
 * significant error. In many cases, we are able to represent very large domains
 * with only a few hundred coefficients and still maintain a very accurate representation
 * of the data.
 *
 *
 * <h3>Minor modifications</h3>
 * Primarily, for {@code N} a power of 2, this implementation allows values within the
 * range {@code [-N/2,N/2)}, instead of the range {@code [0,N)}. This is a more natural
 * implementation for computers, since it does not require us to manually re-sort our
 * data stream into an unsigned list.
 *
 * Secondarily, Cormode et al suggests performing double arithmetic on every step (
 * the paper suggests that the stream contribution at each level should be scaled before
 * addition). However, a careful analysis of the mathematics allows us to factor out
 * that scaling value, and keep counting in the integer domain. This allows us to
 * perform double arithmetic only when computing coefficients and the corresponding
 * energies, which minimizes the possibility of round-off errors during the
 * streaming of large volumes.
 *
 * <h2>Structural Modifications</h2>
 * Cormode et al. introduced the "GroupedCountSketch" data structure, which uses
 * a three-dimensional array of fixed size to hold counter values with a fixed memory
 * footprint. The size of this array is determined by the <em>tolerance</em> {@code e}
 * and the <em>repitition-factor</em> {@code t}; there are {@code t} copies, each
 * holding {@code 1/e*(1/e^2)} 8-byte counters. This allows one to trade off memory
 * for accuracy--more accuracy required more memory, but one can make an informed
 * tradeoff in terms of the allowed error versus required memory.
 *
 * As a result, each level uses {@code t/(e^3)} counters.
 * For example, a tolerance of 1% and a repitition-factor of 3 requires
 * {@code 3/(0.01^3) = 3,000,000} 8-byte counters, or approx. 22 MB of space at
 * each level. This is clearly large, but better than maintaining the
 * {@code 2^31} 8-byte counters (approx. 16GB) required to maintain the lowest level
 * of the entire domain of possible integers.
 *
 * Thus, there is a level {@code L} such that, for {@code l\< L}, it is more
 * memory efficient to represent each counter individually, but for {@code l>=L},
 * it is more memory efficient to use Cormode et al's GroupedCountSketch structure.
 * This is found by realizing that exact storage requires 1 8-byte counter for each
 * interval held in the level, and each level holds {@code 2^l} intervals. Thus, when
 * {@code 8*(2^l)>t/e^3}, it is more memory efficient to use Cormode et al.'s sketch
 * structure, while levels above that should use an exact count instead.
 *
 * This approach has two advantages over the raw GroupedCountSketch: Firstly, it
 * is more memory efficient, because it uses the smaller exact counts whenever those
 * counters would be smaller than the shared sketch. Secondly, because it uses
 * exact counters whenever possible, it eliminates one source of potential error,
 * improving the overall accuracy of the data structure.
 *
 *
 * @author Scott Fines
 *         Date: 10/23/14
 */
class IntHaarTransform implements IntUpdateable{
    /*
     * The first index is the level, the second index is the group id
     */
    private final Level[] levels;
    private long count = 0l;
    private final int lg;

    private final IntMinMaxCollector boundaryCollector;

    public static IntHaarTransform newCounter(int maxValue,final float tolerance,final int t){
        assert tolerance<1 && tolerance>0: "Tolerance must be between 0 and 1";
        final double sketchSize = 8*t/(Math.pow(tolerance,3))+8*t/tolerance;
        return new IntHaarTransform(maxValue){

            @Override
            protected CounterSet newCounterSet(int level) {
                final long exactSize = 8l*(1<<level);
                CounterSet counter;
                if(exactSize<=sketchSize){
                    counter = new DenseExactCounterSet(level);
                }else {
                    counter = new GroupCountSketch(t, tolerance);
                }
                return counter;
            }
        };
    }

    IntHaarTransform(int maxValue){
        //we allow positive and negative values, so we will have maxValue*2 counters,
        //and lg(maxValue) levels
        int N = 1;
        int lg =0;
        while(N<=maxValue && N>0){
            N<<=1;
            lg++;
        }
        this.lg = lg;
        this.levels = new Level[lg];
        for(int i=0;i<levels.length;i++){
            levels[i] = new Level(i,lg,newCounterSet(i));
        }
        this.boundaryCollector = new IntMinMaxCollector();
    }

    protected CounterSet newCounterSet(int level){
        return new DenseExactCounterSet(level);
    }

    @Override
    public void update(int item) {
        update(item,1l);
    }

    @Override
    public void update(int item, long count) {
        //update each interval independently
        //noinspection ForLoopReplaceableByForEach
        for(int i=0;i<levels.length;i++){
            levels[i].update(item,count);
        }
        boundaryCollector.update(item,count);
        this.count+=count;
    }

    @Override
    public void update(Integer item) {
        update(item,1l);
    }

    @Override
    public void update(Integer item, long count) {
        assert item!=null: "Cannot build a wavelet of a null int!";
        update(item.intValue(),count);
    }

    public IntDoubleOpenHashMap getCoefficients(double support){
        IntDoubleOpenHashMap coefs = new IntDoubleOpenHashMap();
        double avg = count/Math.sqrt(1<<(lg));
        coefs.put(0,avg);
        findHighestCoefs(support,0,0,levels,coefs);
        return coefs;
    }

    public IntRangeQuerySolver build(double threshold){
        return new IntRangeSolver((1<<lg),boundaryCollector,getCoefficients(threshold));
    }

    private void findHighestCoefs(double threshold,int level, int g, Level[] levels, IntDoubleOpenHashMap coefs) {
        if(level>=levels.length) return;
        double energy = levels[level].getEnergy(g);
        if(energy>=threshold){
            int coef = (1<<level)+g;
            coefs.put(coef,levels[level].getValue(g));
            findHighestCoefs(threshold, level+1, 2*g, levels, coefs);
            findHighestCoefs(threshold, level+1, 2*g+1, levels, coefs);
        }
    }

    private static class Level{
        /*multiplicative factor for determining the dyadic interval to which a value belongs*/
        protected final double a;
        /*Additive factor for determining the dyadic interval to which a value belongs*/
        protected final double b;
        /*The height of this level in the tree(counting from 0)*/
        protected final int level;
        /*The total height of the tree--equivalent to lg(N)*/
        protected final int lg;
        /*
         * a and b values for the level "below" this in the tree.
         *
         * We use these values to determine whether or not a specific
         * value falls to the left or the right of the midpoint of its respective
         * dyadic interval (and hence whether or not to make the additive value
         * positive or negative).
         *
         * Theoretically, we could save on extra doubles by storing a reference
         * to the next Level in the tree directly here, then calling to the
         * next level to determine what group it should be. However, that would
         * introduce an awkwardness with accounting for the lowest level, so
         * we don't worry about it--particularly since it is the counters, and
         * not these two doubles which are the main memory cost.
         */
        private final double na;
        private final double nb;

        /*Constant to scale coefficients at this level by*/
        protected final double scale;

        private final CounterSet counter;

        protected Level(int level, int lg,CounterSet counterSet) {
            this.level = level;
            this.lg = lg;
            this.a = 1d/(1<<(lg-level));
            if(level==0)
                this.b = 1d/2;
            else
                this.b = (1<<(level-1));

            this.na = a*2;
            this.nb = b*2;

            this.scale = Math.sqrt(a);
            this.counter = counterSet;
        }

        public void update(int value, long count){
            int group = group(value);
            long cnt = signedCount(value,count);
            counter.update(group,value,cnt);
        }

        public double getEnergy(int group) {
            return scale*counter.energy(group);
        }

        public double getValue(int group) {
            return scale*counter.value(group);
        }

        protected int group(int value) {
            /*
             * return the group which owns this value at this level.
             *
             * Here "group" is synonymous with "dyadic interval"; this
             * method finds n such that the interval [n*2^(l-lgN),(n+1)*2^(l-lgN))
             * contains value (where l is the level in the tree).
             */
            return (int)(a*value+b);
        }

        protected long signedCount(int value,long count) {
            /*
             * Adjust the sign of the count based on whether
             * or not the value is located on the left or
             * the right of the midpoint of this dyadic interval.
             */
            int ng = (int)(na*value+nb);
            return ng%2==0?-count: count;
        }

    }

    private static interface CounterSet{

        double energy(int group);
        void update(int group, int value, long count);
        double value(int group);
    }

    private static class DenseExactCounterSet implements CounterSet{
        private long[] counters;

        public DenseExactCounterSet(int level) {
            this.counters = new long[1<<level];
        }

        @Override
        public double energy(int group) {
            long counter = counters[group];
            return counter*counter;
        }

        @Override
        public void update(int group, int value, long count) {
            counters[group]+=count;
        }

        @Override
        public double value(int group) {
            return counters[group];
        }
    }

    private static class GroupCountSketch implements CounterSet{
        private final int t;
        private final int b;
        private final int c;

        private final Hash32[] h;
        private final Hash32[] f;

        private final long[][][] s;
        private final long[][] values;

        public GroupCountSketch(int t, float epsilon) {
            this.t = t;
            float temp = 1/epsilon;
            int _b = 1;
            while(_b<temp)
                _b<<=1;
            this.b = _b;
            temp /=epsilon;

            _b=1;
            while(_b<temp){
                _b<<=1;
            }
            this.c = _b;

            this.s = new long[t][][];
            this.values = new long[t][];
            this.h = new Hash32[t];
            this.f = new Hash32[t];
            for(int i=0;i<t;i++){
                this.values[i] = new long[b];
                s[i] = new long[b][];
                for(int j=0;j<b;j++){
                    s[i][j] = new long[c];
                }

                h[i] = HashFunctions.murmur3(i);
                f[i] = HashFunctions.murmur3(3*i+2);
            }
        }

        @Override
        public double energy(int group) {
            long[] possibleValues = new long[t];
            for(int i=0;i<t;i++){
                long energy = 0l;
                int hPos = h[i].hash(group) & (b-1);
                for(int j=0;j<c;j++){
                    long l = s[i][hPos][j];
                    energy+=l*l;
                }
                possibleValues[i] = energy;
            }

            return Math.sqrt(MoreArrays.median(possibleValues));
        }

        @Override
        public void update(int group, int value, long count) {
            for(int i=0;i<t;i++){
                int hPos = h[i].hash(group) & (b-1);
                int fPos = f[i].hash(value) & (c-1);
                if(h[i].hash(value)%2==0)
                    s[i][hPos][fPos] +=count;
                else
                    s[i][hPos][fPos] -=count;
                values[i][hPos] +=count;
            }
        }

        @Override
        public double value(int group) {
            long[] possibleValues = new long[t];

            for(int i=0;i<t;i++){
                possibleValues[i] = values[i][h[i].hash(group)&(b-1)];
            }
            return MoreArrays.min(possibleValues);
        }
    }

    public static void main(String... args) throws Exception{

//        int[] signal = new int[]{0,0,2,2,2,2,2,3,3};
        int[] signal = new int[]{0,1,2,3,4,5,6,7};
//        int[] count = new int[]{1,3,5,11,12,13,0,1};
        int[] count = new int[]{2,2,0,2,3,5,4,4};
        int N =8;
//        IntHaarTransform exact = new IntHaarTransform(N);
        IntHaarTransform sketch = IntHaarTransform.newCounter(N,0.01f,5);
        int total=0;
        for(int i=0;i<signal.length;i++){
            sketch.update(signal[i], count[i]);
            total+=count[i];
        }
        System.out.printf("Building the sketch%n");
        IntRangeQuerySolver solver = sketch.build(0.0);
        int rangeSize=1;
        for(int i=-N;i<=N;i+=rangeSize){
            System.out.printf("[%d,%d),est=%d%n",i,i+rangeSize,
                    solver.between(i,i+rangeSize,true,false));
        }
    }
}
