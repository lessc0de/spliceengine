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

package com.splicemachine.hbase.util;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.PeekingIterator;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.regionserver.RegionScanner;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.IOException;
import java.util.*;

import static org.mockito.Mockito.mock;

public abstract class AbstractIteratorRegionScanner implements RegionScanner{
    private final PeekingIterator<Set<Cell>> kvs;
    private final Scan scan;
    private final Filter filter;

    public AbstractIteratorRegionScanner(Iterator<Set<Cell>> kvs,Scan scan) {
        this.kvs = Iterators.peekingIterator(kvs);
        this.scan = scan;
        this.filter = scan.getFilter();
    }

    @Override public HRegionInfo getRegionInfo() { return mock(HRegionInfo.class); }

    @Override
    public boolean isFilterDone() throws IOException {
        return filter!=null && filter.filterAllRemaining();
    }

    @SuppressWarnings("LoopStatementThatDoesntLoop")
    @Override
    public boolean reseek(byte[] row) throws IOException {
        if(!kvs.hasNext()) return false;
        while(kvs.hasNext()){
            Set<Cell> next = kvs.peek();
            if(next.size()<0){
                kvs.next();  //throw empty rows away
                continue;
            }
            for(Cell kv: next){
                if(Bytes.equals(kv.getRowArray(),kv.getRowOffset(),kv.getRowLength(),row,0, row.length))
                    return true;
                else{
                    kvs.next();
                    break;
                }
            }
        }
        return false;
    }

    @Override public long getMvccReadPoint() { return 0; }

    @Override
    public boolean nextRaw(List<Cell> result) throws IOException {
        return next(result);
    }

//    @Override
    public boolean nextRaw(List<Cell> result, int limit) throws IOException {
        return next(result);
    }

    @Override
    public boolean next(List<Cell> results) throws IOException {
        OUTER: while(kvs.hasNext()){
            Set<Cell> next = kvs.next();
            List<Cell> toAdd = Lists.newArrayListWithCapacity(next.size());
            for(Cell kv:next){
                if(!containedInScan(kv)) continue; //skip KVs we aren't interested in
                if(filter!=null){
                    Filter.ReturnCode retCode = filter.filterKeyValue(kv);
                    switch(retCode){
                        case INCLUDE:
                        case INCLUDE_AND_NEXT_COL:
                            toAdd.add(kv);
                            break;
                        case SKIP:
                        case NEXT_COL:
                            break;
                        case NEXT_ROW:
                            continue OUTER;
                        case SEEK_NEXT_USING_HINT:
                            throw new UnsupportedOperationException("DON'T USE THIS");
                    }
                }
            }
            if(filter!=null){
                if(filter.filterRow()) continue;

                filter.filterRowCells(toAdd);
                filter.reset();
            }
            if(toAdd.size()>0){
                results.addAll(toAdd);
                return true;
            }
        }
        return false;
    }

    private boolean containedInScan(Cell kv) {
        byte[] row=kv.getRow();
        if(Bytes.compareTo(scan.getStartRow(),row)>0) return false;
        if(Bytes.compareTo(scan.getStopRow(),row)<=0) return false;
        byte[] family = kv.getFamily();
        Map<byte[], NavigableSet<byte[]>> familyMap = scan.getFamilyMap();
        if(familyMap.size()<=0) return true;

        if(!familyMap.containsKey(family)) return false;
        NavigableSet<byte[]> qualifiersToFetch = familyMap.get(family);
        if(qualifiersToFetch.size()<=0) return true;
        return qualifiersToFetch.contains(kv.getQualifier());
    }



    @Override
    public void close() throws IOException {
        //no-op
    }

//    @Override
    public boolean next(List<Cell> result, int limit) throws IOException {
        return next(result);
    }

    @Override
    public long getMaxResultSize() {
        return Long.MAX_VALUE;
    }
}

