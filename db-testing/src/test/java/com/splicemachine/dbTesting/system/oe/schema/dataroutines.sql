-- Licensed to the Apache Software Foundation (ASF) under one or more
-- contributor license agreements.  See the NOTICE file distributed with
-- this work for additional information regarding copyright ownership.
-- The ASF licenses this file to You under the Apache License, Version 2.0
-- (the "License"); you may not use this file except in compliance with
-- the License.  You may obtain a copy of the License at
--
--    http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
;

-- Routines related to the data model;

-- Function to provided an updated C_DATA column for a customer account.
-- Section 2.5.2.2
CREATE FUNCTION BAD_CREDIT_DATA(
    C_DATA VARCHAR(500), W INT, D INT,
    CW SMALLINT, CD SMALLINT, C_ID INTEGER,
    AMOUNT DECIMAL(6, 2))
RETURNS VARCHAR(500)
LANGUAGE JAVA PARAMETER STYLE JAVA
NO SQL
EXTERNAL NAME 'com.splicemachine.dbTesting.system.oe.routines.Data.dataForBadCredit';
    