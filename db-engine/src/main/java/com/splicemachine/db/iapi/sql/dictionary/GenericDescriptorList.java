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

import com.splicemachine.db.catalog.UUID;

import java.util.ArrayList;

public class GenericDescriptorList<T extends UniqueTupleDescriptor> extends ArrayList<T> {
    private boolean scanned;

    /**
     * Mark whether or not the underlying system table has
     * been scanned.  (If a table does not have any
     * constraints then the size of its CDL will always
     * be 0.  We used these get/set methods to determine
     * when we need to scan the table.
     *
     * @param scanned Whether or not the underlying system table has been scanned.
     */
    public void setScanned(boolean scanned) {
        this.scanned = scanned;
    }

    /**
     * Return whether or not the underlying system table has been scanned.
     *
     * @return Where or not the underlying system table has been scanned.
     */
    public boolean getScanned() {
        return scanned;
    }

    /**
     * Get the UniqueTupleDescriptor that matches the
     * input uuid.
     *
     * @param uuid The UUID for the object
     * @return The matching UniqueTupleDescriptor.
     */
    public UniqueTupleDescriptor getUniqueTupleDescriptor(UUID uuid) {
        for (UniqueTupleDescriptor ud : this) {
            if (ud.getUUID().equals(uuid)) {
                return ud;
            }
        }
        return null;
    }
}
