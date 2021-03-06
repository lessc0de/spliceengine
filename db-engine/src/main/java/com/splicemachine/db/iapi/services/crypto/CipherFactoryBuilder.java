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


package com.splicemachine.db.iapi.services.crypto;
import com.splicemachine.db.iapi.error.StandardException;
import java.util.Properties;


/*
 * Interface to create instances of the cipher factory 
 * based on the user specified encryption properties.
 */

public interface CipherFactoryBuilder
{

    /**
     * Create an instance of the cipher factory.
     *
     * @param create    true, if the database is getting configured 
     *                  for encryption.
     * @param props	    encryption properties/attributes to use
     *                  for creating the cipher factory.
     * @param newAttrs  true, if cipher factory has to be created using 
     *                  the new attributes specified by the user. 
     *                 
     */
    public CipherFactory createCipherFactory(boolean create, 
                                             Properties props, 
                                             boolean newAttrs) 
        throws StandardException;
}
