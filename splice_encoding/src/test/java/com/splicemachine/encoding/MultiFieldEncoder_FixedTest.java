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

package com.splicemachine.encoding;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class MultiFieldEncoder_FixedTest {

    @Test
    public void encodeEmpty() {
        int FIELD_COUNT = 10;

        MultiFieldEncoder multiFieldEncoder = MultiFieldEncoder.create(FIELD_COUNT);

        for (int i = 0; i < FIELD_COUNT; i++) {
            multiFieldEncoder.encodeEmpty();
        }

        byte[] result = multiFieldEncoder.build();

        assertEquals(FIELD_COUNT - 1, result.length);
        assertArrayEquals(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0}, result);
    }

    @Test
    public void encodeNext_withEmpty() {

        MultiFieldEncoder encoder1 = MultiFieldEncoder.create(3);
        encoder1.encodeNext("A");
        encoder1.encodeEmpty();
        encoder1.encodeNext("B");

        MultiFieldEncoder encoder2 = MultiFieldEncoder.create(3);
        encoder2.encodeNext("A");
        encoder2.encodeNext("B");

        MultiFieldEncoder encoder3 = MultiFieldEncoder.create(3);
        encoder3.encodeNext("A");
        encoder3.encodeNext("B");
        encoder3.encodeEmpty();

        MultiFieldEncoder encoder4 = MultiFieldEncoder.create(3);
        encoder4.encodeEmpty();
        encoder4.encodeNext("A");
        encoder4.encodeNext("B");

        assertArrayEquals(new byte[]{67, 0, 0, 68}, encoder1.build());
        assertArrayEquals(new byte[]{67, 0, 68}, encoder2.build());
        assertArrayEquals(new byte[]{67, 0, 68, 0}, encoder3.build());
        assertArrayEquals(new byte[]{0, 67, 0, 68}, encoder4.build());

    }

    @Test
    public void encodeNext_withNull() {

        MultiFieldEncoder encoder1 = MultiFieldEncoder.create(3);
        encoder1.encodeNext("A");
        encoder1.encodeNext((String)null);
        encoder1.encodeNext("B");

        MultiFieldEncoder encoder2 = MultiFieldEncoder.create(3);
        encoder2.encodeNext("A");
        encoder2.encodeNext("B");

        MultiFieldEncoder encoder3 = MultiFieldEncoder.create(3);
        encoder3.encodeNext("A");
        encoder3.encodeNext("B");
        encoder3.encodeNext((String) null);

        MultiFieldEncoder encoder4 = MultiFieldEncoder.create(3);
        encoder4.encodeNext((String) null);
        encoder4.encodeNext("A");
        encoder4.encodeNext("B");

        assertArrayEquals(new byte[]{67, 0, 0, 68}, encoder1.build());
        assertArrayEquals(new byte[]{67, 0, 68}, encoder2.build());
        assertArrayEquals(new byte[]{67, 0, 68, 0}, encoder3.build());
        assertArrayEquals(new byte[]{0, 67, 0, 68}, encoder4.build());

    }


}
