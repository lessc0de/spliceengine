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

import com.splicemachine.db.iapi.sql.compile.Visitable;
import com.splicemachine.db.iapi.sql.compile.Visitor;

import java.util.Vector;

/**
 * Collect all nodes of the designated type to be returned in a vector. <p> Can find any type of node -- the class or
 * class name of the target node is passed in as a constructor parameter.
 */
public class CollectNodesVisitor implements Visitor {

    private Vector nodeList;
    private Class nodeClass;
    private Class skipOverClass;

    /**
     * Construct a visitor
     *
     * @param nodeClass the class of the node that we are looking for.
     */
    public CollectNodesVisitor(Class nodeClass) {
        this.nodeClass = nodeClass;
        nodeList = new Vector();
    }

    /**
     * Construct a visitor
     *
     * @param nodeClass     the class of the node that we are looking for.
     * @param skipOverClass do not go below this node when searching for nodeClass.
     */
    public CollectNodesVisitor(Class nodeClass, Class skipOverClass) {
        this(nodeClass);
        this.skipOverClass = skipOverClass;
    }

    @Override
    public boolean visitChildrenFirst(Visitable node) {
        return false;
    }

    @Override
    public boolean stopTraversal() {
        return false;
    }
    ////////////////////////////////////////////////
    //
    // VISITOR INTERFACE
    //
    ////////////////////////////////////////////////

    /**
     * If we have found the target node, we are done.
     *
     * @param node the node to process
     * @return me
     */
    @Override
    public Visitable visit(Visitable node, QueryTreeNode parent) {
        if (nodeClass.isInstance(node)) {
            nodeList.add(node);
        }
        return node;
    }

    /**
     * Don't visit childen under the skipOverClass node, if it isn't null.
     *
     * @return true/false
     */
    @Override
    public boolean skipChildren(Visitable node) {
        return (skipOverClass != null) && skipOverClass.isInstance(node);
    }

    ////////////////////////////////////////////////
    //
    // CLASS INTERFACE
    //
    ////////////////////////////////////////////////

    /**
     * Return the list of matching nodes.
     */
    public Vector getList() {
        return nodeList;
    }
}
