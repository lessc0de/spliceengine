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

package com.splicemachine.derby.utils.marshall.dvd;

import com.splicemachine.db.iapi.services.loader.ClassFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;

/**
 * Created by jyuan on 11/3/15.
 */
public class UDTInputStream extends ObjectInputStream {

    private ClassFactory cf;

    protected UDTInputStream() throws IOException, SecurityException {
        super();
    }

    public UDTInputStream(InputStream in, ClassFactory cf) throws IOException{
        super(in);
        this.cf = cf;
    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc)
            throws IOException, ClassNotFoundException
    {
        String name = desc.getName();
        return cf.loadApplicationClass(name);
    }
}
