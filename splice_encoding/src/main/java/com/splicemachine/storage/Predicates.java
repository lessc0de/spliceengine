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

package com.splicemachine.storage;

import com.carrotsearch.hppc.ObjectArrayList;
import com.splicemachine.primitives.Bytes;
import com.splicemachine.utils.ByteDataInput;
import com.splicemachine.utils.Pair;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.IOException;

/**
 * @author Scott Fines
 * Created on: 8/12/13
 */
public class Predicates {

    private Predicates(){} //don't construct a utility class

    public static Pair<? extends Predicate,Integer> fromBytes(byte[] bytes, int offset) throws IOException {
        PredicateType type = PredicateType.valueOf(bytes[offset]);
        switch (type) {
            case VALUE:
						case CHAR_VALUE:
                return ValuePredicate.fromBytes(bytes,offset);
            case NULL:
                return NullPredicate.fromBytes(bytes,offset+1);
            case AND:
                return AndPredicate.fromBytes(bytes,offset+1);
            case OR:
                return OrPredicate.fromBytes(bytes,offset+1);
            default:
                return getCustomPredicate(bytes,offset+1);
        }
    }

    public static Pair<ObjectArrayList<Predicate>,Integer> allFromBytes(byte[] bytes, int offset) throws IOException{
        int length = Bytes.bytesToInt(bytes,offset);
        return fromBytes(bytes,offset+4,length);
    }

    public static Pair<ObjectArrayList<Predicate>,Integer> fromBytes(byte[] bytes, int offset, int length) throws IOException{
    	ObjectArrayList<Predicate> predicates = ObjectArrayList.newInstanceWithCapacity(length);
        int currentOffset = offset;
        for(int i=0;i<length;i++){
            Pair<? extends Predicate,Integer> next = fromBytes(bytes,currentOffset);
            currentOffset+=next.getSecond();
            predicates.add(next.getFirst());
        }
        return Pair.newPair(predicates,currentOffset);
    }

    public static byte[] toBytes(Predicate...predicates){
        byte[][] data = new byte[predicates.length][];
        int size = 0;
        for(int i=0;i<predicates.length;i++){
            data[i] = predicates[i].toBytes();
            size+=data[i].length;
        }
        byte[] finalData = new byte[size];
        int offset=0;
        for(byte[] datum:data){
            System.arraycopy(datum,0,finalData,offset,datum.length);
            offset+=datum.length;
        }
        return finalData;
    }

    public static byte[] toBytes(ObjectArrayList<Predicate> predicates){
        byte[][] data = new byte[predicates.size()][];
        int size = 0;
        for(int i=0;i<predicates.size();i++){
            data[i] = predicates.get(i).toBytes();
            size+=data[i].length;
        }
        byte[] finalData = new byte[size+4];
        Bytes.intToBytes(predicates.size(), finalData, 0);
        int offset=4;
        for(byte[] datum:data){
            System.arraycopy(datum,0,finalData,offset,datum.length);
            offset+=datum.length;
        }
        return finalData;
    }

    @SuppressFBWarnings(value = "SR_NOT_CHECKED",justification = "This is a byte[] input stream, " +
            "which never returns less than skipped without breaking other stuff")
    private static Pair<Predicate,Integer> getCustomPredicate(byte[] bytes, int offset) throws IOException {
        try{
            ByteDataInput bdi = new ByteDataInput(bytes);
            bdi.skipBytes(offset);
            return Pair.newPair((Predicate) bdi.readObject(), bdi.available() - offset);
        }catch(ClassNotFoundException cnfe){
            throw new IOException(cnfe);
        }
    }
}
