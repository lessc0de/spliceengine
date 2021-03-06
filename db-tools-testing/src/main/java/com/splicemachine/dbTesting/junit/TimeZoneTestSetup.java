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

package com.splicemachine.dbTesting.junit;

import java.util.TimeZone;
import junit.framework.Test;

/**
 * Decorator that changes the default timezone of the runtime environment
 * for the duration of the test.
 */
public class TimeZoneTestSetup extends BaseTestSetup {
    /** Original timezone. */
    private TimeZone savedDefault;
    /** The timezone to use as default while running the test. */
    private TimeZone requestedDefault;

    /**
     * Wrap a test in a decorator that changes the default timezone.
     * @param test the test to decorate
     * @param timeZoneID the ID of the timezone to use
     */
    public TimeZoneTestSetup(Test test, String timeZoneID) {
        this(test, TimeZone.getTimeZone(timeZoneID));
    }

    /**
     * Wrap a test in a decorator that changes the default timezone.
     * @param test the test to decorate
     * @param zone the timezone to use
     */
    public TimeZoneTestSetup(Test test, TimeZone zone) {
        super(test);
        this.requestedDefault = zone;
    }

    /**
     * Set the timezone.
     */
    protected void setUp() {
        savedDefault = TimeZone.getDefault();
        TimeZone.setDefault(requestedDefault);
    }

    /**
     * Reset the timezone.
     */
    protected void tearDown() {
        TimeZone.setDefault(savedDefault);
        savedDefault = null;
        requestedDefault = null;
    }
}
