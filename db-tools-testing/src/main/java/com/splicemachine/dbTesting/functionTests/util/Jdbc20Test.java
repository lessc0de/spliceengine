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

package com.splicemachine.dbTesting.functionTests.util;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;

/////////////////////////////////////////////////////////////////////
//
//	Tests the new jdbc 20 method on jdbc 20 getCurrentConnection()
//	connection to make sure that this indeed is a 20 connection and
//  not jdbc 1x connection. This test is used by getCurConnJdbc20.sql
//  from functionTests/conn directory.
//
/////////////////////////////////////////////////////////////////////
public class Jdbc20Test { 

	public static void newToJdbc20Method() throws SQLException {
		Connection conn = java.sql.DriverManager.getConnection("jdbc:default:connection");
    	//trying a jdbc20 createStatement. This will only work under jdbc20
    	Statement stmt = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
									 ResultSet.CONCUR_READ_ONLY);
    }
}
