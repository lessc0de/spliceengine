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

package com.splicemachine.db.impl.sql.compile;

import com.splicemachine.db.iapi.error.StandardException;

/**
 * A WindowList represents the list of windows (definitions) for a table
 * expression, either defined explicitly in a WINDOW clause, or inline in the
 * SELECT list or ORDER BY clause.
 *
 */

public class WindowList extends QueryTreeNodeVector<WindowNode> {
    /**
     * @param window the window definition to add to the list
     */
    public void addWindow(WindowNode window)
    {
        addElement(window);
    }

    public void bind(SelectNode selectNode) throws StandardException {
        for (WindowNode wdn : getNodes()) {
           wdn.bind(selectNode);
        }
    }
}
