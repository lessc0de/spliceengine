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

package com.splicemachine.db.iapi.services.loader;

import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.services.context.Context;

/**
	Generated classes must implement this interface.

*/
public interface GeneratedByteCode {

	/**
		Initialize the generated class from a context.
		Called by the class manager just after
		creating the instance of the new class.
	*/
	public void initFromContext(Context context)
		throws StandardException;

	/**
		Set the Generated Class. Call by the class manager just after
		calling initFromContext.
	*/
	public void setGC(GeneratedClass gc);

	/**
		Called by the class manager just after calling setGC().
	*/
	public void postConstructor() throws StandardException;

	/**
		Get the GeneratedClass object for this object.
	*/
	public GeneratedClass getGC();

	public GeneratedMethod getMethod(String methodName) throws StandardException;


	public Object e0() throws StandardException ; 
	public Object e1() throws StandardException ;
	public Object e2() throws StandardException ;
	public Object e3() throws StandardException ;
	public Object e4() throws StandardException ; 
	public Object e5() throws StandardException ;
	public Object e6() throws StandardException ;
	public Object e7() throws StandardException ;
	public Object e8() throws StandardException ; 
	public Object e9() throws StandardException ;

    public String e0ToString() throws StandardException ;
    public String e1ToString() throws StandardException ;
    public String e2ToString() throws StandardException ;
    public String e3ToString() throws StandardException ;
    public String e4ToString() throws StandardException ;
    public String e5ToString() throws StandardException ;
    public String e6ToString() throws StandardException ;
    public String e7ToString() throws StandardException ;
    public String e8ToString() throws StandardException ;
    public String e9ToString() throws StandardException ;
}
