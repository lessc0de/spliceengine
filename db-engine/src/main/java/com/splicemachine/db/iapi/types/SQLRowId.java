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

package com.splicemachine.db.iapi.types;

import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.services.cache.ClassSize;
import com.splicemachine.db.iapi.services.io.ArrayInputStream;
import com.splicemachine.db.iapi.services.io.StoredFormatIds;
import com.splicemachine.db.iapi.services.sanity.SanityManager;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.sql.RowId;

/**
 * Created by jyuan on 10/8/14.
 */
public class SQLRowId extends DataType implements RowLocation, RowId{

    private static final int BASE_MEMORY_USAGE = ClassSize.estimateBaseFromCatalog(SQLRowId.class);
    private static final char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
    byte[] bytes;

    public SQLRowId() {
        bytes = null;
    }

    public SQLRowId(byte[] bytes) {
        setValue(bytes);
    }

    public SQLRowId(SQLRowId other) {
        if (other.bytes != null) {
            byte[] b = new byte[other.bytes.length];
            for (int i = 0; i < other.bytes.length; ++i) {
                b[i] = other.bytes[i];
            }
            setValue(b);
        }
    }

	public void setValue(byte[] bytesArg)
	{
		bytes = bytesArg;
		isNull = evaluateNull();
	}

    public  boolean equals(Object obj) {
        if (this == obj)
            return true;

        if (obj instanceof SQLRowId) {
            SQLRowId other = (SQLRowId) obj;
            if (bytes != null && other.bytes != null && bytes.length == other.bytes.length) {
                for (int i = 0; i < bytes.length; ++i) {
                    if (bytes[i] != other.bytes[i]) {
                        return false;
                    }
                }
                return true;
            }
        }

        return false;
    }

    public  byte[] 	getBytes() {
        return bytes;
    }

    public  int hashCode() {
        return 0;
    }

    public  String 	toString() {
        return toHex(bytes, 0, bytes.length);
    }

    private String toHex(byte[] bytes,int offset,int length) {
        if(bytes==null || length<=0) return "";
        char[] hexChars = new char[length * 2];
        int v;
        for ( int j = 0,k=offset; j < length; k++,j++ ) {
            v = bytes[k] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    @Override
    public void setValueFromResultSet(java.sql.ResultSet resultSet, int colNumber,
                                      boolean isNullable) {
    }

    @Override
    public int getLength() {
        if (bytes != null) {
            return bytes.length;
        }

        return 0;
    }

    @Override
    public int estimateMemoryUsage() {
        return BASE_MEMORY_USAGE;
    }

    @Override
    public DataValueDescriptor cloneValue(boolean forceMaterialization) {
        return new SQLRowId(this);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        if (bytes == null) {
            out.writeInt(0);
        }
        else {
            int len = bytes.length;
            out.writeInt(len);
            for (int i = 0; i < len; ++i) {
                out.writeByte(bytes[i]);
            }
        }
    }

    @Override
    public String getTypeName() {
        return "SQLRowId";
    }

    @Override
    public void readExternal(ObjectInput in)  throws IOException, ClassNotFoundException {
        int len = in.readInt();
        if (len > 0) {
            bytes = new byte[len];
            for (int i = 0; i < len; ++i) {
                bytes[i] = in.readByte();
            }
			isNull = evaluateNull();
        }
    }

    @Override
    public void readExternalFromArray(ArrayInputStream in) throws IOException, ClassNotFoundException {

    }


	private final boolean evaluateNull()
	{
        return bytes == null;
    }

    @Override
    public void restoreToNull() {
    }

    @Override
    public int getTypeFormatId() {
        return StoredFormatIds.ACCESS_HEAP_ROW_LOCATION_V1_ID;
    }

    @Override
    public DataValueDescriptor getNewNull() {
        return new SQLRowId();
    }

    @Override
    public boolean compare(int op,
                           DataValueDescriptor other,
                           boolean orderedNulls,
                           boolean unknownRV) throws StandardException
    {
        // HeapRowLocation should not be null, ignore orderedNulls
        int result = compare(other);

        switch(op)
        {
            case ORDER_OP_LESSTHAN:
                return (result < 0); // this < other
            case ORDER_OP_EQUALS:
                return (result == 0);  // this == other
            case ORDER_OP_LESSOREQUALS:
                return (result <= 0);  // this <= other
            default:

                if (SanityManager.DEBUG)
                    SanityManager.THROWASSERT("Unexpected operation");
                return false;
        }
    }

    public int compare(DataValueDescriptor other) throws StandardException
    {
        if (SanityManager.DEBUG)
            SanityManager.ASSERT(other instanceof SQLRowId);

        SQLRowId arg = (SQLRowId) other;

        // We only care if two rowid equals
        int ret = 0;
        if (this.bytes == null && arg.bytes == null) {
            ret = 0;
        }
        else if (this.bytes == null || arg.bytes == null) {
            ret = -1;
        } else if (this.bytes.length != arg.bytes.length){
            ret = -1;
        }
        else {
            for (int i = 0; i < bytes.length; ++i) {
                if (this.bytes[i] != arg.bytes[i]) {
                    ret = -1;
                    break;
                }
            }
        }
        return ret;
    }

    @Override
    public String getString() {
        return toString();
    }
}
