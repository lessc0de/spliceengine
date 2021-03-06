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

package com.splicemachine.derby.test.framework;

import com.splicemachine.access.HConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.HBaseAdmin;

import java.io.IOException;

/**
 * @author Scott Fines
 *         Date: 6/7/16
 */
public class RegionUtils{

    public static void splitTable(long conglomId) throws IOException, InterruptedException{
        TableName tn =TableName.valueOf("splice",Long.toString(conglomId));
        try(HBaseAdmin admin = new HBaseAdmin(HConfiguration.unwrapDelegate())){
            int startSize = admin.getTableRegions(tn).size();
            admin.split(Long.toString(conglomId));
            while(admin.getTableRegions(tn).size()<startSize){
                Thread.sleep(200);
            }
        }
    }
}
