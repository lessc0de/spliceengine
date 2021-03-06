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

package com.splicemachine.test_dao;

import org.junit.Assert;

import java.sql.Connection;
import java.util.List;

/**
 * Query sys.systriggers.
 */
public class TriggerDAO {

    private JDBCTemplate jdbcTemplate;

    public TriggerDAO(Connection connection) {
        this.jdbcTemplate = new JDBCTemplate(connection);
    }

    /**
     * Count number of defined triggers with the specified name.
     */
    public long count(String triggerName) {
        List<Long> count = jdbcTemplate.query("" +
                "select count(*) from sys.systriggers t " +
                "join sys.sysschemas s on s.schemaid=t.schemaid " +
                "where triggername=? and schemaname=CURRENT SCHEMA", triggerName.toUpperCase());
        return count.get(0);
    }

    /**
     * Throws assertion error if the specified trigger does not exist in the current schema.
     */
    public void assertTriggerExists(String... triggerNames) {
        for (String triggerName : triggerNames) {
            Assert.assertTrue("expected trigger to exist = " + triggerName, count(triggerName) == 1);
        }
    }

    /**
     * Throws assertion error if the specified trigger does not exist in the current schema.
     */
    public void assertTriggerGone(String... triggerNames) {
        for (String triggerName : triggerNames) {
            Assert.assertTrue("expected trigger NOT to exist = " + triggerName, count(triggerName) == 0);
        }
    }

    /**
     * Drop the specified triggers.
     */
    public void drop(String... triggerNames) {
        for (String triggerName : triggerNames) {
            jdbcTemplate.executeUpdate("DROP TRIGGER " + triggerName);
        }
    }

    /**
     * Returns the names of all triggers on the specified table
     */
    public List<String> getTriggerNames(String schemaName, String tableName) {
        return jdbcTemplate.query("" +
                "select trig.triggername " +
                "from sys.systriggers trig " +
                "join sys.sysschemas  s    on trig.schemaid = s.schemaid " +
                "join sys.systables   tab  on trig.tableid  = tab.tableid " +
                "where s.schemaname=? and tab.tablename=?", schemaName.toUpperCase(), tableName.toUpperCase());
    }

    /**
     * Drop all triggers on the specified table.
     */
    public void dropAllTriggers(String schemaName, String tableName) {
        for (String triggerName : getTriggerNames(schemaName, tableName)) {
            drop(triggerName);
        }
    }

}
