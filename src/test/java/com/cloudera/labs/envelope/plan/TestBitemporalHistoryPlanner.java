/**
 * Copyright © 2016-2017 Cloudera, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cloudera.labs.envelope.plan;

import static com.cloudera.labs.envelope.plan.BitemporalHistoryPlanner.CURRENT_FLAG_FIELD_NAME_CONFIG_NAME;
import static com.cloudera.labs.envelope.plan.BitemporalHistoryPlanner.CURRENT_FLAG_NO;
import static com.cloudera.labs.envelope.plan.BitemporalHistoryPlanner.CURRENT_FLAG_YES;
import static com.cloudera.labs.envelope.plan.BitemporalHistoryPlanner.EVENT_TIME_EFFECTIVE_FROM_FIELD_NAME_CONFIG_NAME;
import static com.cloudera.labs.envelope.plan.BitemporalHistoryPlanner.EVENT_TIME_EFFECTIVE_TO_FIELD_NAME_CONFIG_NAME;
import static com.cloudera.labs.envelope.plan.BitemporalHistoryPlanner.FAR_FUTURE_MILLIS;
import static com.cloudera.labs.envelope.plan.BitemporalHistoryPlanner.KEY_FIELD_NAMES_CONFIG_NAME;
import static com.cloudera.labs.envelope.plan.BitemporalHistoryPlanner.SYSTEM_TIME_EFFECTIVE_FROM_FIELD_NAME_CONFIG_NAME;
import static com.cloudera.labs.envelope.plan.BitemporalHistoryPlanner.SYSTEM_TIME_EFFECTIVE_TO_FIELD_NAME_CONFIG_NAME;
import static com.cloudera.labs.envelope.plan.BitemporalHistoryPlanner.TIMESTAMP_FIELD_NAME_CONFIG_NAME;
import static com.cloudera.labs.envelope.plan.BitemporalHistoryPlanner.VALUE_FIELD_NAMES_CONFIG_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.apache.spark.sql.Row;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.Before;
import org.junit.Test;

import com.cloudera.labs.envelope.spark.RowWithSchema;
import com.cloudera.labs.envelope.utils.RowUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

public class TestBitemporalHistoryPlanner {

  private List<Row> arriving;
  private List<Row> existing;
  private StructType keySchema;
  private StructType arrivingSchema;
  private StructType existingSchema;
  private StructType existingSchemaWithoutCurrentFlag;
  private Config config;
  private Config configWithoutCurrentFlag;
  private RandomPlanner p;
  private long preplanSystemTime;

  @Before
  public void before() { 
    arriving = Lists.newArrayList();
    existing = Lists.newArrayList();

    keySchema = DataTypes.createStructType(Lists.newArrayList(
      DataTypes.createStructField("key", DataTypes.StringType, false)));
    arrivingSchema = DataTypes.createStructType(Lists.newArrayList(
      DataTypes.createStructField("key", DataTypes.StringType, false),
      DataTypes.createStructField("value", DataTypes.StringType, true),
      DataTypes.createStructField("timestamp", DataTypes.LongType, false)));
    existingSchema = DataTypes.createStructType(Lists.newArrayList(
      DataTypes.createStructField("key", DataTypes.StringType, false),
      DataTypes.createStructField("value", DataTypes.StringType, false),
      DataTypes.createStructField("timestamp", DataTypes.LongType, false),
      DataTypes.createStructField("eventstart", DataTypes.LongType, false),
      DataTypes.createStructField("eventend", DataTypes.LongType, false),
      DataTypes.createStructField("systemstart", DataTypes.LongType, false),
      DataTypes.createStructField("systemend", DataTypes.LongType, false),
      DataTypes.createStructField("currentflag", DataTypes.StringType, false)));
    existingSchemaWithoutCurrentFlag = DataTypes.createStructType(Lists.newArrayList(
      DataTypes.createStructField("key", DataTypes.StringType, false),
      DataTypes.createStructField("value", DataTypes.StringType, false),
      DataTypes.createStructField("timestamp", DataTypes.LongType, false),
      DataTypes.createStructField("eventstart", DataTypes.LongType, false),
      DataTypes.createStructField("eventend", DataTypes.LongType, false),
      DataTypes.createStructField("systemstart", DataTypes.LongType, false),
      DataTypes.createStructField("systemend", DataTypes.LongType, false)));

    Map<String, Object> configMap = Maps.newHashMap();
    configMap.put(KEY_FIELD_NAMES_CONFIG_NAME, Lists.newArrayList("key"));
    configMap.put(VALUE_FIELD_NAMES_CONFIG_NAME, Lists.newArrayList("value"));
    configMap.put(TIMESTAMP_FIELD_NAME_CONFIG_NAME, "timestamp");
    configMap.put(EVENT_TIME_EFFECTIVE_FROM_FIELD_NAME_CONFIG_NAME, "eventstart");
    configMap.put(EVENT_TIME_EFFECTIVE_TO_FIELD_NAME_CONFIG_NAME, "eventend");
    configMap.put(SYSTEM_TIME_EFFECTIVE_FROM_FIELD_NAME_CONFIG_NAME, "systemstart");
    configMap.put(SYSTEM_TIME_EFFECTIVE_TO_FIELD_NAME_CONFIG_NAME, "systemend");
    configMap.put(CURRENT_FLAG_FIELD_NAME_CONFIG_NAME, "currentflag");
    config = ConfigFactory.parseMap(configMap);

    configMap.remove(CURRENT_FLAG_FIELD_NAME_CONFIG_NAME);
    configWithoutCurrentFlag = ConfigFactory.parseMap(configMap);

    preplanSystemTime = System.currentTimeMillis();
  }

  @Test
  public void testOneArrivingNoneExisting() {
    p = new BitemporalHistoryPlanner();
    p.configure(config);

    arriving.add(new RowWithSchema(arrivingSchema, "a", "hello", 100L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 1);

    assertEquals(planned.get(0).getMutationType(), MutationType.INSERT);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertTrue((Long)RowUtils.get(planned.get(0).getRow(), "systemstart") >= preplanSystemTime);
    assertTrue((Long)RowUtils.get(planned.get(0).getRow(), "systemstart") < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "currentflag"), CURRENT_FLAG_YES);
  }

  @Test
  public void testOneArrivingNoneExistingNoCurrentFlag() {
    p = new BitemporalHistoryPlanner();
    p.configure(configWithoutCurrentFlag);

    arriving.add(new RowWithSchema(arrivingSchema, "a", "hello", 100L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 1);

    assertEquals(planned.get(0).getMutationType(), MutationType.INSERT);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertTrue((Long)RowUtils.get(planned.get(0).getRow(), "systemstart") >= preplanSystemTime);
    assertTrue((Long)RowUtils.get(planned.get(0).getRow(), "systemstart") < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), FAR_FUTURE_MILLIS);
  }

  @Test
  public void testMultipleArrivingNoneExisting() {
    p = new BitemporalHistoryPlanner();
    p.configure(config);

    arriving.add(new RowWithSchema(arrivingSchema, "a", "hello", 100L));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world", 200L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 2);  
    assertEquals(planned.get(0).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(1).getMutationType(), MutationType.INSERT);

    Long systemStart0 = (Long)RowUtils.get(planned.get(0).getRow(), "systemstart");
    Long systemStart1 = (Long)RowUtils.get(planned.get(1).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), 199L);
    assertTrue(systemStart0 >= preplanSystemTime);
    assertTrue(systemStart0 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(1).getRow(), "value"), "world");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventstart"), 200L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertTrue(systemStart1 >= preplanSystemTime);
    assertTrue(systemStart1 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "currentflag"), CURRENT_FLAG_YES);
  }

  @Test
  public void testMultipleArrivingNoneExistingNoCurrentFlag() {
    p = new BitemporalHistoryPlanner();
    p.configure(configWithoutCurrentFlag);

    arriving.add(new RowWithSchema(arrivingSchema, "a", "hello", 100L));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world", 200L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 2);
    assertEquals(planned.get(0).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(1).getMutationType(), MutationType.INSERT);

    Long systemStart0 = (Long)RowUtils.get(planned.get(0).getRow(), "systemstart");
    Long systemStart1 = (Long)RowUtils.get(planned.get(1).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), 199L);
    assertTrue(systemStart0 >= preplanSystemTime);
    assertTrue(systemStart0 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), FAR_FUTURE_MILLIS);

    assertEquals(RowUtils.get(planned.get(1).getRow(), "value"), "world");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventstart"), 200L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertTrue(systemStart1 >= preplanSystemTime);
    assertTrue(systemStart1 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemend"), FAR_FUTURE_MILLIS);
  }

  @Test
  public void testOneArrivingOneExistingWhereArrivingLaterThanExisting() {
    p = new BitemporalHistoryPlanner();
    p.configure(config);

    existing.add(new RowWithSchema(existingSchema, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, FAR_FUTURE_MILLIS, CURRENT_FLAG_YES));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world", 200L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 3);
    assertEquals(planned.get(0).getMutationType(), MutationType.UPDATE);
    assertEquals(planned.get(1).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(2).getMutationType(), MutationType.INSERT);

    Long systemStart1 = (Long)RowUtils.get(planned.get(1).getRow(), "systemstart");
    Long systemStart2 = (Long)RowUtils.get(planned.get(2).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemstart"), 1L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart1));
    assertEquals(RowUtils.get(planned.get(0).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(1).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventend"), 199L);
    assertTrue(systemStart1 >= preplanSystemTime);
    assertTrue(systemStart1 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(2).getRow(), "value"), "world");
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventstart"), 200L);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertTrue(systemStart2 >= preplanSystemTime);
    assertTrue(systemStart2 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "currentflag"), CURRENT_FLAG_YES);
  }

  @Test
  public void testOneArrivingOneExistingWhereArrivingLaterThanExistingNoCurrentFlag() {
    p = new BitemporalHistoryPlanner();
    p.configure(configWithoutCurrentFlag);

    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, FAR_FUTURE_MILLIS));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world", 200L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 3);
    assertEquals(planned.get(0).getMutationType(), MutationType.UPDATE);
    assertEquals(planned.get(1).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(2).getMutationType(), MutationType.INSERT);

    Long systemStart1 = (Long)RowUtils.get(planned.get(1).getRow(), "systemstart");
    Long systemStart2 = (Long)RowUtils.get(planned.get(2).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemstart"), 1L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart1));

    assertEquals(RowUtils.get(planned.get(1).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventend"), 199L);
    assertTrue(systemStart1 >= preplanSystemTime);
    assertTrue(systemStart1 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemend"), FAR_FUTURE_MILLIS);

    assertEquals(RowUtils.get(planned.get(2).getRow(), "value"), "world");
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventstart"), 200L);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertTrue(systemStart2 >= preplanSystemTime);
    assertTrue(systemStart2 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "systemend"), FAR_FUTURE_MILLIS);
  }

  @Test
  public void testOneArrivingOneExistingWhereArrivingSameTimeAsExistingWithSameValues() {
    p = new BitemporalHistoryPlanner();
    p.configure(config);

    existing.add(new RowWithSchema(existingSchema, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, FAR_FUTURE_MILLIS, CURRENT_FLAG_YES));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "hello", 100L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 0);
  }

  @Test
  public void testOneArrivingOneExistingWhereArrivingSameTimeAsExistingWithSameValuesNoCurrentFlag() {
    p = new BitemporalHistoryPlanner();
    p.configure(config);

    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, FAR_FUTURE_MILLIS));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "hello", 100L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 0);
  }

  @Test
  public void testOneArrivingOneExistingWhereArrivingSameTimeAsExistingWithDifferentValues() {
    p = new BitemporalHistoryPlanner();
    p.configure(config);

    existing.add(new RowWithSchema(existingSchema, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, FAR_FUTURE_MILLIS, CURRENT_FLAG_YES));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world", 100L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 2);
    assertEquals(planned.get(0).getMutationType(), MutationType.UPDATE);
    assertEquals(planned.get(1).getMutationType(), MutationType.INSERT);

    Long systemStart1 = (Long)RowUtils.get(planned.get(1).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemstart"), 1L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart1));
    assertEquals(RowUtils.get(planned.get(0).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(1).getRow(), "value"), "world");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertTrue(systemStart1 >= preplanSystemTime);
    assertTrue(systemStart1 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "currentflag"), CURRENT_FLAG_YES);
  }

  @Test
  public void testOneArrivingOneExistingWhereArrivingSameTimeAsExistingWithDifferentValuesNoCurrentFlag() {
    p = new BitemporalHistoryPlanner();
    p.configure(configWithoutCurrentFlag);

    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, FAR_FUTURE_MILLIS));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world", 100L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 2);
    assertEquals(planned.get(0).getMutationType(), MutationType.UPDATE);
    assertEquals(planned.get(1).getMutationType(), MutationType.INSERT);

    Long systemStart1 = (Long)RowUtils.get(planned.get(1).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemstart"), 1L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart1));

    assertEquals(RowUtils.get(planned.get(1).getRow(), "value"), "world");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertTrue(systemStart1 >= preplanSystemTime);
    assertTrue(systemStart1 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemend"), FAR_FUTURE_MILLIS);
  }

  @Test
  public void testOneArrivingOneExistingWhereArrivingEarlierThanExisting() {
    p = new BitemporalHistoryPlanner();
    p.configure(config);

    existing.add(new RowWithSchema(existingSchema, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, FAR_FUTURE_MILLIS, CURRENT_FLAG_YES));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world", 50L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 1);
    assertEquals(planned.get(0).getMutationType(), MutationType.INSERT);

    Long systemStart0 = (Long)RowUtils.get(planned.get(0).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "world");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 50L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), 99L);
    assertTrue(systemStart0 >= preplanSystemTime);
    assertTrue(systemStart0 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "currentflag"), CURRENT_FLAG_NO);
  }

  @Test
  public void testOneArrivingOneExistingWhereArrivingEarlierThanExistingNoCurrentFlag() {
    p = new BitemporalHistoryPlanner();
    p.configure(configWithoutCurrentFlag);

    existing.add(new RowWithSchema(existingSchema, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, FAR_FUTURE_MILLIS));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world", 50L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 1);
    assertEquals(planned.get(0).getMutationType(), MutationType.INSERT);

    Long systemStart0 = (Long)RowUtils.get(planned.get(0).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "world");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 50L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), 99L);
    assertTrue(systemStart0 >= preplanSystemTime);
    assertTrue(systemStart0 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), FAR_FUTURE_MILLIS);
  }

  @Test
  public void testOneArrivingMultipleExistingWhereArrivingLaterThanAllExisting() {
    p = new BitemporalHistoryPlanner();
    p.configure(config);

    existing.add(new RowWithSchema(existingSchema, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, 2L, CURRENT_FLAG_NO));
    existing.add(new RowWithSchema(existingSchema, "a", "hello", 100L, 100L, 199L, 3L, FAR_FUTURE_MILLIS, CURRENT_FLAG_NO));
    existing.add(new RowWithSchema(existingSchema, "a", "hello!", 200L, 200L, FAR_FUTURE_MILLIS, 3L, 4L, CURRENT_FLAG_NO));
    existing.add(new RowWithSchema(existingSchema, "a", "hello!", 200L, 200L, 299L, 5L, FAR_FUTURE_MILLIS, CURRENT_FLAG_NO));
    existing.add(new RowWithSchema(existingSchema, "a", "hello?", 300L, 300L, FAR_FUTURE_MILLIS, 5L, FAR_FUTURE_MILLIS, CURRENT_FLAG_YES));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world", 400L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 3);
    assertEquals(planned.get(0).getMutationType(), MutationType.UPDATE);
    assertEquals(planned.get(1).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(2).getMutationType(), MutationType.INSERT);

    Long systemStart1 = (Long)RowUtils.get(planned.get(1).getRow(), "systemstart");
    Long systemStart2 = (Long)RowUtils.get(planned.get(2).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "hello?");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 300L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemstart"), 5L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart1));
    assertEquals(RowUtils.get(planned.get(0).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(1).getRow(), "value"), "hello?");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventstart"), 300L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventend"), 399L);
    assertTrue(systemStart1 >= preplanSystemTime);
    assertTrue(systemStart1 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(2).getRow(), "value"), "world");
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventstart"), 400L);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertTrue(systemStart2 >= preplanSystemTime);
    assertTrue(systemStart2 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "currentflag"), CURRENT_FLAG_YES);
  }

  @Test
  public void testOneArrivingMultipleExistingWhereArrivingLaterThanAllExistingNoCurrentFlag() {
    p = new BitemporalHistoryPlanner();
    p.configure(configWithoutCurrentFlag);

    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, 2L));
    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello", 100L, 100L, 199L, 3L, FAR_FUTURE_MILLIS));
    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello!", 200L, 200L, FAR_FUTURE_MILLIS, 3L, 4L));
    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello!", 200L, 200L, 299L, 5L, FAR_FUTURE_MILLIS));
    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello?", 300L, 300L, FAR_FUTURE_MILLIS, 5L, FAR_FUTURE_MILLIS));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world", 400L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 3);
    assertEquals(planned.get(0).getMutationType(), MutationType.UPDATE);
    assertEquals(planned.get(1).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(2).getMutationType(), MutationType.INSERT);

    Long systemStart1 = (Long)RowUtils.get(planned.get(1).getRow(), "systemstart");
    Long systemStart2 = (Long)RowUtils.get(planned.get(2).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "hello?");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 300L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemstart"), 5L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart1));

    assertEquals(RowUtils.get(planned.get(1).getRow(), "value"), "hello?");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventstart"), 300L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventend"), 399L);
    assertTrue(systemStart1 >= preplanSystemTime);
    assertTrue(systemStart1 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemend"), FAR_FUTURE_MILLIS);

    assertEquals(RowUtils.get(planned.get(2).getRow(), "value"), "world");
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventstart"), 400L);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertTrue(systemStart2 >= preplanSystemTime);
    assertTrue(systemStart2 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "systemend"), FAR_FUTURE_MILLIS);
  }

  @Test
  public void testOneArrivingMultipleExistingWhereArrivingSameTimeAsLatestExistingWithSameValues() {
    p = new BitemporalHistoryPlanner();
    p.configure(config);

    existing.add(new RowWithSchema(existingSchema, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, 2L, CURRENT_FLAG_NO));
    existing.add(new RowWithSchema(existingSchema, "a", "hello", 100L, 100L, 199L, 3L, FAR_FUTURE_MILLIS, CURRENT_FLAG_NO));
    existing.add(new RowWithSchema(existingSchema, "a", "hello!", 200L, 200L, FAR_FUTURE_MILLIS, 3L, 4L, CURRENT_FLAG_NO));
    existing.add(new RowWithSchema(existingSchema, "a", "hello!", 200L, 200L, 299L, 5L, FAR_FUTURE_MILLIS, CURRENT_FLAG_NO));
    existing.add(new RowWithSchema(existingSchema, "a", "hello?", 300L, 300L, FAR_FUTURE_MILLIS, 5L, FAR_FUTURE_MILLIS, CURRENT_FLAG_YES));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "hello?", 300L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 0);
  }

  @Test
  public void testOneArrivingMultipleExistingWhereArrivingSameTimeAsLatestExistingWithSameValuesNoCurrentFlag() {
    p = new BitemporalHistoryPlanner();
    p.configure(configWithoutCurrentFlag);

    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, 2L));
    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello", 100L, 100L, 199L, 3L, FAR_FUTURE_MILLIS));
    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello!", 200L, 200L, FAR_FUTURE_MILLIS, 3L, 4L));
    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello!", 200L, 200L, 299L, 5L, FAR_FUTURE_MILLIS));
    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello?", 300L, 300L, FAR_FUTURE_MILLIS, 5L, FAR_FUTURE_MILLIS));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "hello?", 300L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 0);
  }

  @Test
  public void testOneArrivingMultipleExistingWhereArrivingSameTimeAsLatestExistingWithDifferentValues() {
    p = new BitemporalHistoryPlanner();
    p.configure(config);

    existing.add(new RowWithSchema(existingSchema, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, 2L, CURRENT_FLAG_NO));
    existing.add(new RowWithSchema(existingSchema, "a", "hello", 100L, 100L, 199L, 3L, FAR_FUTURE_MILLIS, CURRENT_FLAG_NO));
    existing.add(new RowWithSchema(existingSchema, "a", "hello!", 200L, 200L, FAR_FUTURE_MILLIS, 3L, 4L, CURRENT_FLAG_NO));
    existing.add(new RowWithSchema(existingSchema, "a", "hello!", 200L, 200L, 299L, 5L, FAR_FUTURE_MILLIS, CURRENT_FLAG_NO));
    existing.add(new RowWithSchema(existingSchema, "a", "hello?", 300L, 300L, FAR_FUTURE_MILLIS, 5L, FAR_FUTURE_MILLIS, CURRENT_FLAG_YES));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world", 300L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 2);
    assertEquals(planned.get(0).getMutationType(), MutationType.UPDATE);
    assertEquals(planned.get(1).getMutationType(), MutationType.INSERT);

    Long systemStart1 = (Long)RowUtils.get(planned.get(1).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "hello?");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 300L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemstart"), 5L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart1));
    assertEquals(RowUtils.get(planned.get(0).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(1).getRow(), "value"), "world");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventstart"), 300L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertTrue(systemStart1 >= preplanSystemTime);
    assertTrue(systemStart1 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "currentflag"), CURRENT_FLAG_YES);
  }

  @Test
  public void testOneArrivingMultipleExistingWhereArrivingSameTimeAsLatestExistingWithDifferentValuesNoCurrentFlag() {
    p = new BitemporalHistoryPlanner();
    p.configure(configWithoutCurrentFlag);

    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, 2L));
    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello", 100L, 100L, 199L, 3L, FAR_FUTURE_MILLIS));
    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello!", 200L, 200L, FAR_FUTURE_MILLIS, 3L, 4L));
    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello!", 200L, 200L, 299L, 5L, FAR_FUTURE_MILLIS));
    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello?", 300L, 300L, FAR_FUTURE_MILLIS, 5L, FAR_FUTURE_MILLIS));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world", 300L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 2);
    assertEquals(planned.get(0).getMutationType(), MutationType.UPDATE);
    assertEquals(planned.get(1).getMutationType(), MutationType.INSERT);

    Long systemStart1 = (Long)RowUtils.get(planned.get(1).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "hello?");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 300L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemstart"), 5L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart1));

    assertEquals(RowUtils.get(planned.get(1).getRow(), "value"), "world");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventstart"), 300L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertTrue(systemStart1 >= preplanSystemTime);
    assertTrue(systemStart1 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemend"), FAR_FUTURE_MILLIS);
  }

  @Test
  public void testOneArrivingMultipleExistingWhereArrivingBetweenTwoExisting() {
    p = new BitemporalHistoryPlanner();
    p.configure(config);

    existing.add(new RowWithSchema(existingSchema, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, 2L, CURRENT_FLAG_NO));
    existing.add(new RowWithSchema(existingSchema, "a", "hello", 100L, 100L, 199L, 3L, FAR_FUTURE_MILLIS, CURRENT_FLAG_NO));
    existing.add(new RowWithSchema(existingSchema, "a", "hello!", 200L, 200L, FAR_FUTURE_MILLIS, 3L, 4L, CURRENT_FLAG_NO));
    existing.add(new RowWithSchema(existingSchema, "a", "hello!", 200L, 200L, 299L, 5L, FAR_FUTURE_MILLIS, CURRENT_FLAG_NO));
    existing.add(new RowWithSchema(existingSchema, "a", "hello?", 300L, 300L, FAR_FUTURE_MILLIS, 5L, FAR_FUTURE_MILLIS, CURRENT_FLAG_YES));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world", 150L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 3);
    assertEquals(planned.get(0).getMutationType(), MutationType.UPDATE);
    assertEquals(planned.get(1).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(2).getMutationType(), MutationType.INSERT);

    Long systemStart1 = (Long)RowUtils.get(planned.get(1).getRow(), "systemstart");
    Long systemStart2 = (Long)RowUtils.get(planned.get(2).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), 199L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemstart"), 3L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart1));
    assertEquals(RowUtils.get(planned.get(0).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(1).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventend"), 149L);
    assertTrue(systemStart1 >= preplanSystemTime);
    assertTrue(systemStart1 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(2).getRow(), "value"), "world");
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventstart"), 150L);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventend"), 199L);
    assertTrue(systemStart2 >= preplanSystemTime);
    assertTrue(systemStart2 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "currentflag"), CURRENT_FLAG_NO);
  }

  @Test
  public void testOneArrivingMultipleExistingWhereArrivingBetweenTwoExistingNoCurrentFlag() {
    p = new BitemporalHistoryPlanner();
    p.configure(configWithoutCurrentFlag);

    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, 2L));
    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello", 100L, 100L, 199L, 3L, FAR_FUTURE_MILLIS));
    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello!", 200L, 200L, FAR_FUTURE_MILLIS, 3L, 4L));
    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello!", 200L, 200L, 299L, 5L, FAR_FUTURE_MILLIS));
    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello?", 300L, 300L, FAR_FUTURE_MILLIS, 5L, FAR_FUTURE_MILLIS));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world", 150L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 3);
    assertEquals(planned.get(0).getMutationType(), MutationType.UPDATE);
    assertEquals(planned.get(1).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(2).getMutationType(), MutationType.INSERT);

    Long systemStart1 = (Long)RowUtils.get(planned.get(1).getRow(), "systemstart");
    Long systemStart2 = (Long)RowUtils.get(planned.get(2).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), 199L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemstart"), 3L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart1));

    assertEquals(RowUtils.get(planned.get(1).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventend"), 149L);
    assertTrue(systemStart1 >= preplanSystemTime);
    assertTrue(systemStart1 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemend"), FAR_FUTURE_MILLIS);

    assertEquals(RowUtils.get(planned.get(2).getRow(), "value"), "world");
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventstart"), 150L);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventend"), 199L);
    assertTrue(systemStart2 >= preplanSystemTime);
    assertTrue(systemStart2 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "systemend"), FAR_FUTURE_MILLIS);
  }

  @Test
  public void testOneArrivingMultipleExistingWhereArrivingEarlierThanAllExisting() {
    p = new BitemporalHistoryPlanner();
    p.configure(config);

    existing.add(new RowWithSchema(existingSchema, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, 2L, CURRENT_FLAG_NO));
    existing.add(new RowWithSchema(existingSchema, "a", "hello", 100L, 100L, 199L, 3L, FAR_FUTURE_MILLIS, CURRENT_FLAG_NO));
    existing.add(new RowWithSchema(existingSchema, "a", "hello!", 200L, 200L, FAR_FUTURE_MILLIS, 3L, 4L, CURRENT_FLAG_NO));
    existing.add(new RowWithSchema(existingSchema, "a", "hello!", 200L, 200L, 299L, 5L, FAR_FUTURE_MILLIS, CURRENT_FLAG_NO));
    existing.add(new RowWithSchema(existingSchema, "a", "hello?", 300L, 300L, FAR_FUTURE_MILLIS, 5L, FAR_FUTURE_MILLIS, CURRENT_FLAG_YES));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world", 50L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 1);

    Long systemStart0 = (Long)RowUtils.get(planned.get(0).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "world");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 50L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), 99L);
    assertTrue(systemStart0 >= preplanSystemTime);
    assertTrue(systemStart0 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "currentflag"), CURRENT_FLAG_NO);
  }

  @Test
  public void testOneArrivingMultipleExistingWhereArrivingEarlierThanAllExistingNoCurrentFlag() {
    p = new BitemporalHistoryPlanner();
    p.configure(configWithoutCurrentFlag);

    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, 2L));
    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello", 100L, 100L, 199L, 3L, FAR_FUTURE_MILLIS));
    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello!", 200L, 200L, FAR_FUTURE_MILLIS, 3L, 4L));
    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello!", 200L, 200L, 299L, 5L, FAR_FUTURE_MILLIS));
    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello?", 300L, 300L, FAR_FUTURE_MILLIS, 5L, FAR_FUTURE_MILLIS));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world", 50L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 1);

    Long systemStart0 = (Long)RowUtils.get(planned.get(0).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "world");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 50L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), 99L);
    assertTrue(systemStart0 >= preplanSystemTime);
    assertTrue(systemStart0 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), FAR_FUTURE_MILLIS);
  }

  @Test
  public void testMultipleArrivingOneExistingWhereAllArrivingLaterThanExisting() {
    p = new BitemporalHistoryPlanner();
    p.configure(config);

    existing.add(new RowWithSchema(existingSchema, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, FAR_FUTURE_MILLIS, CURRENT_FLAG_YES));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world", 200L));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world!", 300L));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world?", 400L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 5);

    Long systemStart1 = (Long)RowUtils.get(planned.get(1).getRow(), "systemstart");
    Long systemStart2 = (Long)RowUtils.get(planned.get(2).getRow(), "systemstart");
    Long systemStart3 = (Long)RowUtils.get(planned.get(3).getRow(), "systemstart");
    Long systemStart4 = (Long)RowUtils.get(planned.get(4).getRow(), "systemstart");

    assertEquals(planned.get(0).getMutationType(), MutationType.UPDATE);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemstart"), 1L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart1));
    assertEquals(RowUtils.get(planned.get(0).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(planned.get(1).getMutationType(), MutationType.INSERT);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventend"), 199L);
    assertTrue(systemStart1 >= preplanSystemTime);
    assertTrue(systemStart1 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(planned.get(2).getMutationType(), MutationType.INSERT);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "value"), "world");
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventstart"), 200L);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventend"), 299L);
    assertTrue(systemStart2 >= preplanSystemTime);
    assertTrue(systemStart2 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(planned.get(3).getMutationType(), MutationType.INSERT);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "value"), "world!");
    assertEquals(RowUtils.get(planned.get(3).getRow(), "eventstart"), 300L);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "eventend"), 399L);
    assertTrue(systemStart3 >= preplanSystemTime);
    assertTrue(systemStart3 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(planned.get(4).getMutationType(), MutationType.INSERT);
    assertEquals(RowUtils.get(planned.get(4).getRow(), "value"), "world?");
    assertEquals(RowUtils.get(planned.get(4).getRow(), "eventstart"), 400L);
    assertEquals(RowUtils.get(planned.get(4).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertTrue(systemStart4 >= preplanSystemTime);
    assertTrue(systemStart4 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(4).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(4).getRow(), "currentflag"), CURRENT_FLAG_YES);
  }

  @Test
  public void testMultipleArrivingOneExistingWhereAllArrivingLaterThanExistingNoCurrentFlag() {
    p = new BitemporalHistoryPlanner();
    p.configure(configWithoutCurrentFlag);

    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, FAR_FUTURE_MILLIS));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world", 200L));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world!", 300L));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world?", 400L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 5);

    Long systemStart1 = (Long)RowUtils.get(planned.get(1).getRow(), "systemstart");
    Long systemStart2 = (Long)RowUtils.get(planned.get(2).getRow(), "systemstart");
    Long systemStart3 = (Long)RowUtils.get(planned.get(3).getRow(), "systemstart");
    Long systemStart4 = (Long)RowUtils.get(planned.get(4).getRow(), "systemstart");

    assertEquals(planned.get(0).getMutationType(), MutationType.UPDATE);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemstart"), 1L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart1));

    assertEquals(planned.get(1).getMutationType(), MutationType.INSERT);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventend"), 199L);
    assertTrue(systemStart1 >= preplanSystemTime);
    assertTrue(systemStart1 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemend"), FAR_FUTURE_MILLIS);

    assertEquals(planned.get(2).getMutationType(), MutationType.INSERT);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "value"), "world");
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventstart"), 200L);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventend"), 299L);
    assertTrue(systemStart2 >= preplanSystemTime);
    assertTrue(systemStart2 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "systemend"), FAR_FUTURE_MILLIS);

    assertEquals(planned.get(3).getMutationType(), MutationType.INSERT);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "value"), "world!");
    assertEquals(RowUtils.get(planned.get(3).getRow(), "eventstart"), 300L);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "eventend"), 399L);
    assertTrue(systemStart3 >= preplanSystemTime);
    assertTrue(systemStart3 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "systemend"), FAR_FUTURE_MILLIS);

    assertEquals(planned.get(4).getMutationType(), MutationType.INSERT);
    assertEquals(RowUtils.get(planned.get(4).getRow(), "value"), "world?");
    assertEquals(RowUtils.get(planned.get(4).getRow(), "eventstart"), 400L);
    assertEquals(RowUtils.get(planned.get(4).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertTrue(systemStart4 >= preplanSystemTime);
    assertTrue(systemStart4 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(4).getRow(), "systemend"), FAR_FUTURE_MILLIS);
  }

  @Test
  public void testMultipleArrivingOneExistingWhereOneArrivingSameTimeAsExistingWithSameValuesAndRestArrivingLaterThanExisting() {
    p = new BitemporalHistoryPlanner();
    p.configure(config);

    existing.add(new RowWithSchema(existingSchema, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, FAR_FUTURE_MILLIS, CURRENT_FLAG_YES));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "hello", 100L));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world", 200L));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world!", 300L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 4);
    assertEquals(planned.get(0).getMutationType(), MutationType.UPDATE);
    assertEquals(planned.get(1).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(2).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(3).getMutationType(), MutationType.INSERT);

    Long systemStart1 = (Long)RowUtils.get(planned.get(1).getRow(), "systemstart");
    Long systemStart2 = (Long)RowUtils.get(planned.get(2).getRow(), "systemstart");
    Long systemStart3 = (Long)RowUtils.get(planned.get(3).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemstart"), 1L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart1));
    assertEquals(RowUtils.get(planned.get(0).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(1).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventend"), 199L);
    assertTrue(systemStart1 >= preplanSystemTime);
    assertTrue(systemStart1 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(2).getRow(), "value"), "world");
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventstart"), 200L);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventend"), 299L);
    assertTrue(systemStart2 >= preplanSystemTime);
    assertTrue(systemStart2 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(3).getRow(), "value"), "world!");
    assertEquals(RowUtils.get(planned.get(3).getRow(), "eventstart"), 300L);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertTrue(systemStart3 >= preplanSystemTime);
    assertTrue(systemStart3 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "currentflag"), CURRENT_FLAG_YES);
  }

  @Test
  public void testMultipleArrivingOneExistingWhereOneArrivingSameTimeAsExistingWithSameValuesAndRestArrivingLaterThanExistingNoCurrentFlag() {
    p = new BitemporalHistoryPlanner();
    p.configure(configWithoutCurrentFlag);

    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, FAR_FUTURE_MILLIS));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "hello", 100L));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world", 200L));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world!", 300L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 4);
    assertEquals(planned.get(0).getMutationType(), MutationType.UPDATE);
    assertEquals(planned.get(1).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(2).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(3).getMutationType(), MutationType.INSERT);

    Long systemStart1 = (Long)RowUtils.get(planned.get(1).getRow(), "systemstart");
    Long systemStart2 = (Long)RowUtils.get(planned.get(2).getRow(), "systemstart");
    Long systemStart3 = (Long)RowUtils.get(planned.get(3).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemstart"), 1L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart1));

    assertEquals(RowUtils.get(planned.get(1).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventend"), 199L);
    assertTrue(systemStart1 >= preplanSystemTime);
    assertTrue(systemStart1 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemend"), FAR_FUTURE_MILLIS);

    assertEquals(RowUtils.get(planned.get(2).getRow(), "value"), "world");
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventstart"), 200L);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventend"), 299L);
    assertTrue(systemStart2 >= preplanSystemTime);
    assertTrue(systemStart2 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "systemend"), FAR_FUTURE_MILLIS);

    assertEquals(RowUtils.get(planned.get(3).getRow(), "value"), "world!");
    assertEquals(RowUtils.get(planned.get(3).getRow(), "eventstart"), 300L);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertTrue(systemStart3 >= preplanSystemTime);
    assertTrue(systemStart3 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "systemend"), FAR_FUTURE_MILLIS);
  }

  @Test
  public void testMultipleArrivingOneExistingWhereOneArrivingSameTimeAsExistingWithDifferentValuesAndRestArrivingLaterThanExisting() {
    p = new BitemporalHistoryPlanner();
    p.configure(config);

    existing.add(new RowWithSchema(existingSchema, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, FAR_FUTURE_MILLIS, CURRENT_FLAG_YES));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world", 100L));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world!", 200L));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world?", 300L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 4);
    assertEquals(planned.get(0).getMutationType(), MutationType.UPDATE);
    assertEquals(planned.get(1).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(2).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(3).getMutationType(), MutationType.INSERT);

    Long systemStart1 = (Long)RowUtils.get(planned.get(1).getRow(), "systemstart");
    Long systemStart2 = (Long)RowUtils.get(planned.get(2).getRow(), "systemstart");
    Long systemStart3 = (Long)RowUtils.get(planned.get(3).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemstart"), 1L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart1));
    assertEquals(RowUtils.get(planned.get(0).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(1).getRow(), "value"), "world");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventend"), 199L);
    assertTrue(systemStart1 >= preplanSystemTime);
    assertTrue(systemStart1 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(2).getRow(), "value"), "world!");
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventstart"), 200L);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventend"), 299L);
    assertTrue(systemStart2 >= preplanSystemTime);
    assertTrue(systemStart2 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(3).getRow(), "value"), "world?");
    assertEquals(RowUtils.get(planned.get(3).getRow(), "eventstart"), 300L);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertTrue(systemStart3 >= preplanSystemTime);
    assertTrue(systemStart3 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "currentflag"), CURRENT_FLAG_YES);
  }

  @Test
  public void testMultipleArrivingOneExistingWhereOneArrivingSameTimeAsExistingWithDifferentValuesAndRestArrivingLaterThanExistingNoCurrentFlag() {
    p = new BitemporalHistoryPlanner();
    p.configure(configWithoutCurrentFlag);

    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, FAR_FUTURE_MILLIS));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world", 100L));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world!", 200L));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world?", 300L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 4);
    assertEquals(planned.get(0).getMutationType(), MutationType.UPDATE);
    assertEquals(planned.get(1).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(2).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(3).getMutationType(), MutationType.INSERT);

    Long systemStart1 = (Long)RowUtils.get(planned.get(1).getRow(), "systemstart");
    Long systemStart2 = (Long)RowUtils.get(planned.get(2).getRow(), "systemstart");
    Long systemStart3 = (Long)RowUtils.get(planned.get(3).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemstart"), 1L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart1));

    assertEquals(RowUtils.get(planned.get(1).getRow(), "value"), "world");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventend"), 199L);
    assertTrue(systemStart1 >= preplanSystemTime);
    assertTrue(systemStart1 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemend"), FAR_FUTURE_MILLIS);

    assertEquals(RowUtils.get(planned.get(2).getRow(), "value"), "world!");
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventstart"), 200L);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventend"), 299L);
    assertTrue(systemStart2 >= preplanSystemTime);
    assertTrue(systemStart2 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "systemend"), FAR_FUTURE_MILLIS);

    assertEquals(RowUtils.get(planned.get(3).getRow(), "value"), "world?");
    assertEquals(RowUtils.get(planned.get(3).getRow(), "eventstart"), 300L);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertTrue(systemStart3 >= preplanSystemTime);
    assertTrue(systemStart3 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "systemend"), FAR_FUTURE_MILLIS);
  }

  @Test
  public void testMultipleArrivingMultipleExistingWhereAllArrivingSameTimeAsExistingWithSameValues() {
    p = new BitemporalHistoryPlanner();
    p.configure(config);

    existing.add(new RowWithSchema(existingSchema, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, 2L, CURRENT_FLAG_NO));
    existing.add(new RowWithSchema(existingSchema, "a", "hello", 100L, 100L, 199L, 3L, FAR_FUTURE_MILLIS, CURRENT_FLAG_NO));
    existing.add(new RowWithSchema(existingSchema, "a", "hello!", 200L, 200L, FAR_FUTURE_MILLIS, 3L, 4L, CURRENT_FLAG_NO));
    existing.add(new RowWithSchema(existingSchema, "a", "hello!", 200L, 200L, 299L, 5L, FAR_FUTURE_MILLIS, CURRENT_FLAG_NO));
    existing.add(new RowWithSchema(existingSchema, "a", "hello?", 300L, 300L, FAR_FUTURE_MILLIS, 5L, FAR_FUTURE_MILLIS, CURRENT_FLAG_YES));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "hello", 100L));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "hello!", 200L));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "hello?", 300L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 0);
  }

  @Test
  public void testMultipleArrivingMultipleExistingWhereAllArrivingSameTimeAsExistingWithSameValuesNoCurrentFlag() {
    p = new BitemporalHistoryPlanner();
    p.configure(configWithoutCurrentFlag);

    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, 2L));
    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello", 100L, 100L, 199L, 3L, FAR_FUTURE_MILLIS));
    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello!", 200L, 200L, FAR_FUTURE_MILLIS, 3L, 4L));
    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello!", 200L, 200L, 299L, 5L, FAR_FUTURE_MILLIS));
    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello?", 300L, 300L, FAR_FUTURE_MILLIS, 5L, FAR_FUTURE_MILLIS));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "hello", 100L));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "hello!", 200L));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "hello?", 300L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 0);
  }

  @Test
  public void testMultipleArrivingMultipleExistingWhereAllArrivingSameTimeAsExistingWithDifferentValues() {
    p = new BitemporalHistoryPlanner();
    p.configure(config);

    existing.add(new RowWithSchema(existingSchema, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, 2L, CURRENT_FLAG_NO));
    existing.add(new RowWithSchema(existingSchema, "a", "hello", 100L, 100L, 199L, 3L, FAR_FUTURE_MILLIS, CURRENT_FLAG_NO));
    existing.add(new RowWithSchema(existingSchema, "a", "hello!", 200L, 200L, FAR_FUTURE_MILLIS, 3L, 4L, CURRENT_FLAG_NO));
    existing.add(new RowWithSchema(existingSchema, "a", "hello!", 200L, 200L, 299L, 5L, FAR_FUTURE_MILLIS, CURRENT_FLAG_NO));
    existing.add(new RowWithSchema(existingSchema, "a", "hello?", 300L, 300L, FAR_FUTURE_MILLIS, 5L, FAR_FUTURE_MILLIS, CURRENT_FLAG_YES));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world", 100L));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world!", 200L));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world?", 300L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 6);
    assertEquals(planned.get(0).getMutationType(), MutationType.UPDATE);
    assertEquals(planned.get(1).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(2).getMutationType(), MutationType.UPDATE);
    assertEquals(planned.get(3).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(4).getMutationType(), MutationType.UPDATE);
    assertEquals(planned.get(5).getMutationType(), MutationType.INSERT);

    Long systemStart1 = (Long)RowUtils.get(planned.get(1).getRow(), "systemstart");
    Long systemStart3 = (Long)RowUtils.get(planned.get(3).getRow(), "systemstart");
    Long systemStart5 = (Long)RowUtils.get(planned.get(5).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), 199L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemstart"), 3L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart1));
    assertEquals(RowUtils.get(planned.get(0).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(1).getRow(), "value"), "world");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventend"), 199L);
    assertTrue(systemStart1 >= preplanSystemTime);
    assertTrue(systemStart1 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(2).getRow(), "value"), "hello!");
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventstart"), 200L);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventend"), 299L);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "systemstart"), 5L);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart3));
    assertEquals(RowUtils.get(planned.get(2).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(3).getRow(), "value"), "world!");
    assertEquals(RowUtils.get(planned.get(3).getRow(), "eventstart"), 200L);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "eventend"), 299L);
    assertTrue(systemStart3 >= preplanSystemTime);
    assertTrue(systemStart3 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(4).getRow(), "value"), "hello?");
    assertEquals(RowUtils.get(planned.get(4).getRow(), "eventstart"), 300L);
    assertEquals(RowUtils.get(planned.get(4).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(4).getRow(), "systemstart"), 5L);
    assertEquals(RowUtils.get(planned.get(4).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart5));
    assertEquals(RowUtils.get(planned.get(4).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(5).getRow(), "value"), "world?");
    assertEquals(RowUtils.get(planned.get(5).getRow(), "eventstart"), 300L);
    assertEquals(RowUtils.get(planned.get(5).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertTrue(systemStart5 >= preplanSystemTime);
    assertTrue(systemStart5 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(5).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(5).getRow(), "currentflag"), CURRENT_FLAG_YES);

  }

  @Test
  public void testMultipleArrivingMultipleExistingWhereAllArrivingSameTimeAsExistingWithDifferentValuesNoCurrentFlag() {
    p = new BitemporalHistoryPlanner();
    p.configure(configWithoutCurrentFlag);

    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, 2L));
    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello", 100L, 100L, 199L, 3L, FAR_FUTURE_MILLIS));
    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello!", 200L, 200L, FAR_FUTURE_MILLIS, 3L, 4L));
    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello!", 200L, 200L, 299L, 5L, FAR_FUTURE_MILLIS));
    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello?", 300L, 300L, FAR_FUTURE_MILLIS, 5L, FAR_FUTURE_MILLIS));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world", 100L));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world!", 200L));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "world?", 300L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 6);
    assertEquals(planned.get(0).getMutationType(), MutationType.UPDATE);
    assertEquals(planned.get(1).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(2).getMutationType(), MutationType.UPDATE);
    assertEquals(planned.get(3).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(4).getMutationType(), MutationType.UPDATE);
    assertEquals(planned.get(5).getMutationType(), MutationType.INSERT);

    Long systemStart1 = (Long)RowUtils.get(planned.get(1).getRow(), "systemstart");
    Long systemStart3 = (Long)RowUtils.get(planned.get(3).getRow(), "systemstart");
    Long systemStart5 = (Long)RowUtils.get(planned.get(5).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), 199L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemstart"), 3L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart1));

    assertEquals(RowUtils.get(planned.get(1).getRow(), "value"), "world");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventend"), 199L);
    assertTrue(systemStart1 >= preplanSystemTime);
    assertTrue(systemStart1 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemend"), FAR_FUTURE_MILLIS);

    assertEquals(RowUtils.get(planned.get(2).getRow(), "value"), "hello!");
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventstart"), 200L);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventend"), 299L);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "systemstart"), 5L);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart3));

    assertEquals(RowUtils.get(planned.get(3).getRow(), "value"), "world!");
    assertEquals(RowUtils.get(planned.get(3).getRow(), "eventstart"), 200L);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "eventend"), 299L);
    assertTrue(systemStart3 >= preplanSystemTime);
    assertTrue(systemStart3 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "systemend"), FAR_FUTURE_MILLIS);

    assertEquals(RowUtils.get(planned.get(4).getRow(), "value"), "hello?");
    assertEquals(RowUtils.get(planned.get(4).getRow(), "eventstart"), 300L);
    assertEquals(RowUtils.get(planned.get(4).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(4).getRow(), "systemstart"), 5L);
    assertEquals(RowUtils.get(planned.get(4).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart5));

    assertEquals(RowUtils.get(planned.get(5).getRow(), "value"), "world?");
    assertEquals(RowUtils.get(planned.get(5).getRow(), "eventstart"), 300L);
    assertEquals(RowUtils.get(planned.get(5).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertTrue(systemStart5 >= preplanSystemTime);
    assertTrue(systemStart5 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(5).getRow(), "systemend"), FAR_FUTURE_MILLIS);

  }

  @Test
  public void testNoneArrivingNoneExisting() {
    p = new BitemporalHistoryPlanner();
    p.configure(config);

    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 0);
  }

  @Test
  public void testNoneArrivingOneExisting() {
    p = new BitemporalHistoryPlanner();
    p.configure(config);

    existing.add(new RowWithSchema(existingSchema, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, FAR_FUTURE_MILLIS, CURRENT_FLAG_YES));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 0);
  }

  @Test
  public void testNoneArrivingOneExistingNoCurrentFlag() {
    p = new BitemporalHistoryPlanner();
    p.configure(configWithoutCurrentFlag);

    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, FAR_FUTURE_MILLIS));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 0);
  }

  @Test
  public void testCarryForwardWhenNull() {
    p = new BitemporalHistoryPlanner();
    config = config.withValue(BitemporalHistoryPlanner.CARRY_FORWARD_CONFIG_NAME, ConfigValueFactory.fromAnyRef(true));
    p.configure(config);
    
    existing.add(new RowWithSchema(existingSchema, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, FAR_FUTURE_MILLIS, CURRENT_FLAG_YES, ""));
    arriving.add(new RowWithSchema(arrivingSchema, "a", null, 200L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 3);
    assertEquals(planned.get(0).getMutationType(), MutationType.UPDATE);
    assertEquals(planned.get(1).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(2).getMutationType(), MutationType.INSERT);

    Long systemStart1 = (Long)RowUtils.get(planned.get(1).getRow(), "systemstart");
    Long systemStart2 = (Long)RowUtils.get(planned.get(2).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemstart"), 1L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart1));
    assertEquals(RowUtils.get(planned.get(0).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(1).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventend"), 199L);
    assertTrue(systemStart1 >= preplanSystemTime);
    assertTrue(systemStart1 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(2).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventstart"), 200L);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertTrue(systemStart2 >= preplanSystemTime);
    assertTrue(systemStart2 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "currentflag"), CURRENT_FLAG_YES);
  }

  @Test
  public void testCarryForwardWhenNullNoCurrentFlag() {
    p = new BitemporalHistoryPlanner();
    configWithoutCurrentFlag = configWithoutCurrentFlag.withValue(BitemporalHistoryPlanner.CARRY_FORWARD_CONFIG_NAME, ConfigValueFactory.fromAnyRef(true));
    p.configure(configWithoutCurrentFlag);

    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, FAR_FUTURE_MILLIS, ""));
    arriving.add(new RowWithSchema(arrivingSchema, "a", null, 200L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 3);
    assertEquals(planned.get(0).getMutationType(), MutationType.UPDATE);
    assertEquals(planned.get(1).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(2).getMutationType(), MutationType.INSERT);

    Long systemStart1 = (Long)RowUtils.get(planned.get(1).getRow(), "systemstart");
    Long systemStart2 = (Long)RowUtils.get(planned.get(2).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemstart"), 1L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart1));

    assertEquals(RowUtils.get(planned.get(1).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventend"), 199L);
    assertTrue(systemStart1 >= preplanSystemTime);
    assertTrue(systemStart1 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemend"), FAR_FUTURE_MILLIS);

    assertEquals(RowUtils.get(planned.get(2).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventstart"), 200L);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertTrue(systemStart2 >= preplanSystemTime);
    assertTrue(systemStart2 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "systemend"), FAR_FUTURE_MILLIS);
  }
  
  @Test
  public void testNoCarryForwardWhenNull() {
    p = new BitemporalHistoryPlanner();
    p.configure(config);
    
    existing.add(new RowWithSchema(existingSchema, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, FAR_FUTURE_MILLIS, CURRENT_FLAG_YES, ""));
    arriving.add(new RowWithSchema(arrivingSchema, "a", null, 200L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 3);
    assertEquals(planned.get(0).getMutationType(), MutationType.UPDATE);
    assertEquals(planned.get(1).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(2).getMutationType(), MutationType.INSERT);

    Long systemStart1 = (Long)RowUtils.get(planned.get(1).getRow(), "systemstart");
    Long systemStart2 = (Long)RowUtils.get(planned.get(2).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemstart"), 1L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart1));
    assertEquals(RowUtils.get(planned.get(0).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(1).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventend"), 199L);
    assertTrue(systemStart1 >= preplanSystemTime);
    assertTrue(systemStart1 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(2).getRow(), "value"), null);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventstart"), 200L);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertTrue(systemStart2 >= preplanSystemTime);
    assertTrue(systemStart2 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "currentflag"), CURRENT_FLAG_YES);
  }

  @Test
  public void testNoCarryForwardWhenNullNoCurrentFlag() {
    p = new BitemporalHistoryPlanner();
    p.configure(configWithoutCurrentFlag);

    existing.add(new RowWithSchema(existingSchemaWithoutCurrentFlag, "a", "hello", 100L, 100L, FAR_FUTURE_MILLIS, 1L, FAR_FUTURE_MILLIS, ""));
    arriving.add(new RowWithSchema(arrivingSchema, "a", null, 200L));
    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 3);
    assertEquals(planned.get(0).getMutationType(), MutationType.UPDATE);
    assertEquals(planned.get(1).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(2).getMutationType(), MutationType.INSERT);

    Long systemStart1 = (Long)RowUtils.get(planned.get(1).getRow(), "systemstart");
    Long systemStart2 = (Long)RowUtils.get(planned.get(2).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemstart"), 1L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart1));

    assertEquals(RowUtils.get(planned.get(1).getRow(), "value"), "hello");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventend"), 199L);
    assertTrue(systemStart1 >= preplanSystemTime);
    assertTrue(systemStart1 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemend"), FAR_FUTURE_MILLIS);

    assertEquals(RowUtils.get(planned.get(2).getRow(), "value"), null);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventstart"), 200L);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertTrue(systemStart2 >= preplanSystemTime);
    assertTrue(systemStart2 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "systemend"), FAR_FUTURE_MILLIS);
  }

  @Test
  public void testCarryForwardWhenNullMultipleOutOfOrderArriving() {
    p = new BitemporalHistoryPlanner();
    config = config.
        withValue(BitemporalHistoryPlanner.CARRY_FORWARD_CONFIG_NAME, ConfigValueFactory.fromAnyRef(true)).
        withValue(BitemporalHistoryPlanner.VALUE_FIELD_NAMES_CONFIG_NAME, ConfigValueFactory.fromAnyRef(Lists.newArrayList("value1","value2")));
    p.configure(config);

    arrivingSchema = DataTypes.createStructType(Lists.newArrayList(
        DataTypes.createStructField("key", DataTypes.StringType, false),
        DataTypes.createStructField("value1", DataTypes.StringType, true),
        DataTypes.createStructField("value2", DataTypes.StringType, true),
        DataTypes.createStructField("timestamp", DataTypes.LongType, false)));
    existingSchema = DataTypes.createStructType(Lists.newArrayList(
        DataTypes.createStructField("key", DataTypes.StringType, false),
        DataTypes.createStructField("value1", DataTypes.StringType, false),
        DataTypes.createStructField("value2", DataTypes.StringType, false),
        DataTypes.createStructField("timestamp", DataTypes.LongType, false),
        DataTypes.createStructField("eventstart", DataTypes.LongType, false),
        DataTypes.createStructField("eventend", DataTypes.LongType, false),
        DataTypes.createStructField("systemstart", DataTypes.LongType, false),
        DataTypes.createStructField("systemend", DataTypes.LongType, false),
        DataTypes.createStructField("currentflag", DataTypes.StringType, false)));

    existing.add(new RowWithSchema(existingSchema, "a", "hello1:100", "hello2:100", 100L, 100L, FAR_FUTURE_MILLIS, 1L, FAR_FUTURE_MILLIS, CURRENT_FLAG_YES));
    arriving.add(new RowWithSchema(arrivingSchema, "a", null, "hello2:200", 200L));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "hello1:150", null, 150L));

    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 4);
    assertEquals(planned.get(0).getMutationType(), MutationType.UPDATE);
    assertEquals(planned.get(1).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(2).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(3).getMutationType(), MutationType.INSERT);

    Long systemStart1 = (Long)RowUtils.get(planned.get(1).getRow(), "systemstart");
    Long systemStart2 = (Long)RowUtils.get(planned.get(2).getRow(), "systemstart");
    Long systemStart3 = (Long)RowUtils.get(planned.get(3).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value1"), "hello1:100");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "value2"), "hello2:100");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemstart"), 1L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart1));
    assertEquals(RowUtils.get(planned.get(0).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(1).getRow(), "value1"), "hello1:100");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "value2"), "hello2:100");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventend"), 149L);
    assertTrue(systemStart1 >= preplanSystemTime);
    assertTrue(systemStart1 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(2).getRow(), "value1"), "hello1:150");
    assertEquals(RowUtils.get(planned.get(2).getRow(), "value2"), "hello2:100");
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventstart"), 150L);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventend"), 199L);
    assertTrue(systemStart2 >= preplanSystemTime);
    assertTrue(systemStart2 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(3).getRow(), "value1"), "hello1:150");
    assertEquals(RowUtils.get(planned.get(3).getRow(), "value2"), "hello2:200");
    assertEquals(RowUtils.get(planned.get(3).getRow(), "eventstart"), 200L);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertTrue(systemStart3 >= preplanSystemTime);
    assertTrue(systemStart3 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "currentflag"), CURRENT_FLAG_YES);
  }

  @Test
  public void testCarryForwardWhenNullMultipleOutOfOrderArrivingWithPreceding() {
    p = new BitemporalHistoryPlanner();
    config = config.
        withValue(BitemporalHistoryPlanner.CARRY_FORWARD_CONFIG_NAME, ConfigValueFactory.fromAnyRef(true)).
        withValue(BitemporalHistoryPlanner.VALUE_FIELD_NAMES_CONFIG_NAME, ConfigValueFactory.fromAnyRef(Lists.newArrayList("value1","value2")));
    p.configure(config);

    arrivingSchema = DataTypes.createStructType(Lists.newArrayList(
        DataTypes.createStructField("key", DataTypes.StringType, false),
        DataTypes.createStructField("value1", DataTypes.StringType, true),
        DataTypes.createStructField("value2", DataTypes.StringType, true),
        DataTypes.createStructField("timestamp", DataTypes.LongType, false)));
    existingSchema = DataTypes.createStructType(Lists.newArrayList(
        DataTypes.createStructField("key", DataTypes.StringType, false),
        DataTypes.createStructField("value1", DataTypes.StringType, false),
        DataTypes.createStructField("value2", DataTypes.StringType, false),
        DataTypes.createStructField("timestamp", DataTypes.LongType, false),
        DataTypes.createStructField("eventstart", DataTypes.LongType, false),
        DataTypes.createStructField("eventend", DataTypes.LongType, false),
        DataTypes.createStructField("systemstart", DataTypes.LongType, false),
        DataTypes.createStructField("systemend", DataTypes.LongType, false),
        DataTypes.createStructField("currentflag", DataTypes.StringType, false)));

    existing.add(new RowWithSchema(existingSchema, "a", null, "hello2:100", 100L, 100L, FAR_FUTURE_MILLIS, 1L, FAR_FUTURE_MILLIS, CURRENT_FLAG_YES));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "hello1:50", null, 50L));
    arriving.add(new RowWithSchema(arrivingSchema, "a", null, "hello2:150", 150L));


    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 4);
    assertEquals(planned.get(0).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(1).getMutationType(), MutationType.UPDATE);
    assertEquals(planned.get(2).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(3).getMutationType(), MutationType.INSERT);

    Long systemStart0 = (Long)RowUtils.get(planned.get(0).getRow(), "systemstart");
    Long systemStart2 = (Long)RowUtils.get(planned.get(2).getRow(), "systemstart");
    Long systemStart3 = (Long)RowUtils.get(planned.get(3).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value1"), "hello1:50");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "value2"), null);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "timestamp"), 50L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 50L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), 99L);
    assertTrue(systemStart0 >= preplanSystemTime);
    assertTrue(systemStart0 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(1).getRow(), "value1"), null);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "value2"), "hello2:100");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "timestamp"), 100L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemstart"), 1L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart2));
    assertEquals(RowUtils.get(planned.get(1).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(2).getRow(), "value1"), "hello1:50");
    assertEquals(RowUtils.get(planned.get(2).getRow(), "value2"), "hello2:100");
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventend"), 149L);
    assertTrue(systemStart2 >= preplanSystemTime);
    assertTrue(systemStart2 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(3).getRow(), "value1"), "hello1:50");
    assertEquals(RowUtils.get(planned.get(3).getRow(), "value2"), "hello2:150");
    assertEquals(RowUtils.get(planned.get(3).getRow(), "eventstart"), 150L);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertTrue(systemStart3 >= preplanSystemTime);
    assertTrue(systemStart3 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "currentflag"), CURRENT_FLAG_YES);
  }

  @Test
  public void testNoCarryForwardWhenNullMultipleOutOfOrderArriving() {
    p = new BitemporalHistoryPlanner();
    config = config.
        withValue(BitemporalHistoryPlanner.VALUE_FIELD_NAMES_CONFIG_NAME, ConfigValueFactory.fromAnyRef(Lists.newArrayList("value1","value2")));
    p.configure(config);

    arrivingSchema = DataTypes.createStructType(Lists.newArrayList(
        DataTypes.createStructField("key", DataTypes.StringType, false),
        DataTypes.createStructField("value1", DataTypes.StringType, true),
        DataTypes.createStructField("value2", DataTypes.StringType, true),
        DataTypes.createStructField("timestamp", DataTypes.LongType, false)));
    existingSchema = DataTypes.createStructType(Lists.newArrayList(
        DataTypes.createStructField("key", DataTypes.StringType, false),
        DataTypes.createStructField("value1", DataTypes.StringType, false),
        DataTypes.createStructField("value2", DataTypes.StringType, false),
        DataTypes.createStructField("timestamp", DataTypes.LongType, false),
        DataTypes.createStructField("eventstart", DataTypes.LongType, false),
        DataTypes.createStructField("eventend", DataTypes.LongType, false),
        DataTypes.createStructField("systemstart", DataTypes.LongType, false),
        DataTypes.createStructField("systemend", DataTypes.LongType, false),
        DataTypes.createStructField("currentflag", DataTypes.StringType, false)));

    existing.add(new RowWithSchema(existingSchema, "a", "hello1:100", "hello2:100", 100L, 100L, FAR_FUTURE_MILLIS, 1L, FAR_FUTURE_MILLIS, CURRENT_FLAG_YES));
    arriving.add(new RowWithSchema(arrivingSchema, "a", null, "hello2:200", 200L));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "hello1:150", null, 150L));

    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 4);
    assertEquals(planned.get(0).getMutationType(), MutationType.UPDATE);
    assertEquals(planned.get(1).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(2).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(3).getMutationType(), MutationType.INSERT);

    Long systemStart1 = (Long)RowUtils.get(planned.get(1).getRow(), "systemstart");
    Long systemStart2 = (Long)RowUtils.get(planned.get(2).getRow(), "systemstart");
    Long systemStart3 = (Long)RowUtils.get(planned.get(3).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value1"), "hello1:100");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "value2"), "hello2:100");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemstart"), 1L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart1));
    assertEquals(RowUtils.get(planned.get(0).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(1).getRow(), "value1"), "hello1:100");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "value2"), "hello2:100");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventend"), 149L);
    assertTrue(systemStart1 >= preplanSystemTime);
    assertTrue(systemStart1 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(2).getRow(), "value1"), "hello1:150");
    assertEquals(RowUtils.get(planned.get(2).getRow(), "value2"), null);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventstart"), 150L);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventend"), 199L);
    assertTrue(systemStart2 >= preplanSystemTime);
    assertTrue(systemStart2 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(3).getRow(), "value1"), null);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "value2"), "hello2:200");
    assertEquals(RowUtils.get(planned.get(3).getRow(), "eventstart"), 200L);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertTrue(systemStart3 >= preplanSystemTime);
    assertTrue(systemStart3 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "currentflag"), CURRENT_FLAG_YES);
  }

  @Test
  public void testNoCarryForwardWhenNullMultipleOutOfOrderArrivingWithPreceding() {
    p = new BitemporalHistoryPlanner();
    config = config.
        withValue(BitemporalHistoryPlanner.VALUE_FIELD_NAMES_CONFIG_NAME, ConfigValueFactory.fromAnyRef(Lists.newArrayList("value1","value2")));
    p.configure(config);

    arrivingSchema = DataTypes.createStructType(Lists.newArrayList(
        DataTypes.createStructField("key", DataTypes.StringType, false),
        DataTypes.createStructField("value1", DataTypes.StringType, true),
        DataTypes.createStructField("value2", DataTypes.StringType, true),
        DataTypes.createStructField("timestamp", DataTypes.LongType, false)));
    existingSchema = DataTypes.createStructType(Lists.newArrayList(
        DataTypes.createStructField("key", DataTypes.StringType, false),
        DataTypes.createStructField("value1", DataTypes.StringType, false),
        DataTypes.createStructField("value2", DataTypes.StringType, false),
        DataTypes.createStructField("timestamp", DataTypes.LongType, false),
        DataTypes.createStructField("eventstart", DataTypes.LongType, false),
        DataTypes.createStructField("eventend", DataTypes.LongType, false),
        DataTypes.createStructField("systemstart", DataTypes.LongType, false),
        DataTypes.createStructField("systemend", DataTypes.LongType, false),
        DataTypes.createStructField("currentflag", DataTypes.StringType, false)));

    existing.add(new RowWithSchema(existingSchema, "a", null, "hello2:100", 100L, 100L, FAR_FUTURE_MILLIS, 1L, FAR_FUTURE_MILLIS, CURRENT_FLAG_YES));
    arriving.add(new RowWithSchema(arrivingSchema, "a", "hello1:50", null, 50L));
    arriving.add(new RowWithSchema(arrivingSchema, "a", null, "hello2:150", 150L));


    Row key = new RowWithSchema(keySchema, "a");

    List<PlannedRow> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 4);
    assertEquals(planned.get(0).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(1).getMutationType(), MutationType.UPDATE);
    assertEquals(planned.get(2).getMutationType(), MutationType.INSERT);
    assertEquals(planned.get(3).getMutationType(), MutationType.INSERT);

    Long systemStart0 = (Long)RowUtils.get(planned.get(0).getRow(), "systemstart");
    Long systemStart2 = (Long)RowUtils.get(planned.get(2).getRow(), "systemstart");
    Long systemStart3 = (Long)RowUtils.get(planned.get(3).getRow(), "systemstart");

    assertEquals(RowUtils.get(planned.get(0).getRow(), "value1"), "hello1:50");
    assertEquals(RowUtils.get(planned.get(0).getRow(), "value2"), null);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "timestamp"), 50L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventstart"), 50L);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "eventend"), 99L);
    assertTrue(systemStart0 >= preplanSystemTime);
    assertTrue(systemStart0 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(0).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(1).getRow(), "value1"), null);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "value2"), "hello2:100");
    assertEquals(RowUtils.get(planned.get(1).getRow(), "timestamp"), 100L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemstart"), 1L);
    assertEquals(RowUtils.get(planned.get(1).getRow(), "systemend"), RowUtils.precedingTimestamp(systemStart2));
    assertEquals(RowUtils.get(planned.get(1).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(2).getRow(), "value1"), null);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "value2"), "hello2:100");
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventstart"), 100L);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "eventend"), 149L);
    assertTrue(systemStart2 >= preplanSystemTime);
    assertTrue(systemStart2 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(2).getRow(), "currentflag"), CURRENT_FLAG_NO);

    assertEquals(RowUtils.get(planned.get(3).getRow(), "value1"), null);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "value2"), "hello2:150");
    assertEquals(RowUtils.get(planned.get(3).getRow(), "eventstart"), 150L);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "eventend"), FAR_FUTURE_MILLIS);
    assertTrue(systemStart3 >= preplanSystemTime);
    assertTrue(systemStart3 < preplanSystemTime + 5000);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "systemend"), FAR_FUTURE_MILLIS);
    assertEquals(RowUtils.get(planned.get(3).getRow(), "currentflag"), CURRENT_FLAG_YES);
  }


}
