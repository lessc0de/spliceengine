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

package com.splicemachine.pipeline.client;

import com.carrotsearch.hppc.IntObjectOpenHashMap;
import com.carrotsearch.hppc.IntOpenHashSet;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.carrotsearch.hppc.cursors.IntObjectCursor;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.splicemachine.pipeline.context.WriteContext;

/**
 * @author Scott Fines
 * Created on: 8/8/13 
 */
public class BulkWriteResult {
		private WriteResult globalStatus;
		private IntOpenHashSet notRunRows;
		private IntObjectOpenHashMap<WriteResult> failedRows;

		private transient WriteContext writeContext;
		private transient int position;

		public BulkWriteResult() {
				notRunRows = new IntOpenHashSet();
				failedRows = new IntObjectOpenHashMap<>();
		}

		public BulkWriteResult(WriteContext writeContext, WriteResult globalStatus) {
				notRunRows = new IntOpenHashSet();
				failedRows = new IntObjectOpenHashMap<>();
				this.writeContext = writeContext;
				this.globalStatus = globalStatus;
		}

		public BulkWriteResult(WriteResult globalStatus, IntOpenHashSet notRunRows, IntObjectOpenHashMap<WriteResult> failedRows){
				this.notRunRows = notRunRows;
				this.failedRows = failedRows;
				this.globalStatus = globalStatus;
		}

		public BulkWriteResult(WriteResult globalStatus){
				this();
				this.globalStatus = globalStatus;
		}

		public IntObjectOpenHashMap<WriteResult> getFailedRows() {
				return failedRows;
		}

		public IntOpenHashSet getNotRunRows() {
				return notRunRows;
		}

		public void addResult(int pos, WriteResult result) {
				switch (result.getCode()) {
						case SUCCESS:
								return; //return nothing for success
						case NOT_RUN:
								notRunRows.add(pos);
								break;
						default:
								failedRows.put(pos,result);
				}
		}

		public WriteResult getGlobalResult() {
				return globalStatus;
		}

		public void setGlobalStatus(WriteResult globalStatus) {
				this.globalStatus = globalStatus;
		}

		public WriteContext getWriteContext() {
				return writeContext;
		}

		public int getPosition() {
				return position;
		}

		public void setPosition(int position) {
				this.position = position;
		}

		@Override
		public String toString() {
				return "BulkWriteResult{" +
						"globalStatus=" + (globalStatus==null?"null":globalStatus.toString()) + 
						", notRunRows=" + (notRunRows==null?"null":notRunRows.size()) +
						", failedRows=" + (failedRows==null?"null":failedRows.size()) +
						", writeContext=" + (writeContext==null?"null":writeContext.toString()) +
						'}';
		}

		public static Serializer<BulkWriteResult> kryoSerializer(){
				return SERIALIZER;
		}

		private static final Serializer<BulkWriteResult> SERIALIZER = new Serializer<BulkWriteResult>() {
				@Override
				public void write(Kryo kryo, Output output, BulkWriteResult object) {
						kryo.writeObject(output,object.globalStatus);
						output.writeInt(object.notRunRows.size());
						for(IntCursor cursor:object.notRunRows){
								output.writeInt(cursor.value);
						}
						output.writeInt(object.failedRows.size());
						for(IntObjectCursor<WriteResult> c:object.failedRows){
								output.writeInt(c.key);
								kryo.writeObject(output,c.value);
						}
				}

				@Override
				public BulkWriteResult read(Kryo kryo, Input input, Class<BulkWriteResult> type) {
						WriteResult globalStatus = kryo.readObject(input,WriteResult.class);
						int notRunSize = input.readInt();
						IntOpenHashSet notRunRows = new IntOpenHashSet(notRunSize);
						for(int i=0;i<notRunSize;i++){
								notRunRows.add(input.readInt());
						}
						int failedSize = input.readInt();
						IntObjectOpenHashMap<WriteResult> failedRows = new IntObjectOpenHashMap<>(failedSize,0.9f);
						for(int i=0;i<failedSize;i++){
								int k = input.readInt();
								WriteResult result = kryo.readObject(input,WriteResult.class);
								failedRows.put(k,result);
						}
						return new BulkWriteResult(globalStatus,notRunRows,failedRows);
				}
		};
}