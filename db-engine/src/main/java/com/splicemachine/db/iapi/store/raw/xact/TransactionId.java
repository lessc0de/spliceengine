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

package com.splicemachine.db.iapi.store.raw.xact;

import com.splicemachine.db.iapi.services.io.Formatable;

/**
	A transaction identifier that is only unique within a raw store, do not
	ever pass this out of raw store.  During reboot, all transaction Ids that
	have ever generated a log record will not be reused.  

	However, if you put away the transaction Id of a read only transaction,
	then the is no guarentee that the transactionId won't be reused when the
	system reboots.  It is much safer to store away the ExternalTrasanctionId
	rather than the transactionId.

	The equals() method for TransactionId implements by value equality.

	MT - immutable

*/
public interface TransactionId extends Formatable {

	/** 
		Return the maximum number of bytes the transactionId will take
		to store using writeExternal.
	*/
	int getMaxStoredSize();

	/* need to write a value based HashCode() method. */
}
