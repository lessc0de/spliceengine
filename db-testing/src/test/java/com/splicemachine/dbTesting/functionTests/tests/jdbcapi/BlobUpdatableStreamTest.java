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
package com.splicemachine.dbTesting.functionTests.tests.jdbcapi;

import java.io.InputStream;
import java.sql.Blob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import junit.framework.Test;
import com.splicemachine.dbTesting.functionTests.util.streams.LoopingAlphabetStream;
import com.splicemachine.dbTesting.junit.BaseJDBCTestCase;
import com.splicemachine.dbTesting.junit.TestConfiguration;

/**
 * Test if blob stream updates itself to point to LOBInputStream
 * if the blob is updated after fetching the stream.
 */
public class BlobUpdatableStreamTest extends BaseJDBCTestCase {
    public BlobUpdatableStreamTest (String name) {
        super (name);
    }

    public void testUpdatableBlob () throws Exception {
        getConnection().setAutoCommit (false);
        PreparedStatement ps = prepareStatement ("insert into testblob " +
                "values (?)");
        //insert a large blob to ensure dvd gives out a stream and not
        //a byte array
        ps.setBinaryStream (1, new LoopingAlphabetStream (1024 * 1024), 1024 * 1024);
        ps.executeUpdate();
        ps.close();
        Statement stmt = createStatement ();
        ResultSet rs = stmt.executeQuery("select * from testblob");
        rs.next();
        Blob blob = rs.getBlob (1);
        InputStream is = blob.getBinaryStream();
        long l = is.skip (20);
        //truncate blob after accessing original stream
        blob.truncate (l);
        int ret = is.read();
        //should not be able to read after truncated value
        assertEquals ("stream update failed", -1, ret);
        byte [] buffer = new byte [1024];
        for (int i = 0; i < buffer.length; i++)
            buffer [i] = (byte) (i % 255);
        blob.setBytes (blob.length() + 1, buffer);
        byte [] buff = new byte [1024];
        int toRead = 1024;
        while (toRead != 0 ) {
            long read = is.read (buff, 1024 - toRead, toRead);
            if (read < 0)
                fail ("couldn't retrieve updated value");
            toRead -= read;
        }
        for (int i = 0; i < buffer.length; i++) {
            assertEquals ("value retrieved is not same as updated value",
                buffer [i], buff [i]);
        }
        blob = null;
        rs.close();
        stmt.close();
        commit();
    }

    public static Test suite () {
        return TestConfiguration.defaultSuite (
                BlobUpdatableStreamTest.class);
    }

    public void setUp() throws  Exception {
        Statement stmt = createStatement();
        stmt.execute ("create table testblob (data blob)");
        stmt.close();
    }

    protected void tearDown() throws Exception {
        Statement stmt = createStatement();
        stmt.execute ("drop table testblob");
        stmt.close();
        commit ();
        super.tearDown();
    }
}
