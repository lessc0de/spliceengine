
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

package com.splicemachine.db.iapi.sql.dictionary;

import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.catalog.UUID;
import com.splicemachine.db.iapi.sql.conn.LanguageConnectionContext;
import com.splicemachine.db.iapi.sql.Activation;

/**
 * This class describes a routine execute permission
 * required by a statement.
 */

public final class StatementRoutinePermission extends StatementPermission
{
	private UUID routineUUID;

	public StatementRoutinePermission( UUID routineUUID)
	{
		this.routineUUID = routineUUID;
	}
									 
	/**
	 * Return routine UUID for this access descriptor
	 *
	 * @return	Routine UUID
	 */
	public UUID getRoutineUUID()
	{
		return routineUUID;
	}

	/**
	 * @see StatementPermission#check
	 */
	public void check( LanguageConnectionContext lcc,
					   boolean forGrant,
					   Activation activation) throws StandardException
	{
        genericCheck( lcc, forGrant, activation, "EXECUTE" );
	}

	/**
	 * @see StatementPermission#isCorrectPermission
	 */
    public boolean isCorrectPermission( PermissionsDescriptor raw )
    {
        if ( (raw == null) || !( raw instanceof RoutinePermsDescriptor) ) { return false; }

        RoutinePermsDescriptor pd = (RoutinePermsDescriptor) raw;
        
        return pd.getHasExecutePermission();
    }

	/**
	 * @see StatementPermission#getPrivilegedObject
	 */
    public PrivilegedSQLObject getPrivilegedObject( DataDictionary dd ) throws StandardException
    { return dd.getAliasDescriptor( routineUUID); }

	/**
	 * @see StatementPermission#getPermissionDescriptor
	 */
	public PermissionsDescriptor getPermissionDescriptor(String authid, DataDictionary dd)
	throws StandardException
	{
		return dd.getRoutinePermissions(routineUUID,authid);
	}

	/**
	 * @see StatementPermission#getObjectType
	 */
    public String getObjectType() { return "ROUTINE"; }

	public String toString()
	{
		return "StatementRoutinePermission: " + routineUUID;
	}
}
