/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.protocol.core.impl.wireformat;

import java.util.Objects;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.utils.DataConstants;

public class ReplicationLargeMessageEndMessage extends PacketImpl {

   long messageId;
   long pendingRecordId;
   /**
    * True = delete file, False = close file
    */
   private boolean isDelete;

   public ReplicationLargeMessageEndMessage() {
      super(PacketImpl.REPLICATION_LARGE_MESSAGE_END);
   }

   public ReplicationLargeMessageEndMessage(final long messageId, final long pendingRecordId, final boolean isDelete) {
      this();
      this.messageId = messageId;
      /*
       * We use a negative value to indicate that this id is pre-generated by primary node so that it won't be generated
       * at the backup. See https://issues.apache.org/jira/browse/ARTEMIS-1221.
       */
      this.pendingRecordId = -pendingRecordId;
      this.isDelete = isDelete;
   }

   @Override
   public int expectedEncodeSize() {
      return PACKET_HEADERS_SIZE +
         DataConstants.SIZE_LONG + // buffer.writeLong(messageId)
         DataConstants.SIZE_LONG + // buffer.writeLong(pendingRecordId);
         DataConstants.SIZE_BOOLEAN; // buffer.writeBoolean(isDelete);
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeLong(messageId);
      buffer.writeLong(pendingRecordId);
      buffer.writeBoolean(isDelete);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      messageId = buffer.readLong();
      if (buffer.readableBytes() >= DataConstants.SIZE_LONG) {
         pendingRecordId = buffer.readLong();
      }
      if (buffer.readableBytes() >= DataConstants.SIZE_BOOLEAN) {
         isDelete = buffer.readBoolean();
      }
   }

   public long getMessageId() {
      return messageId;
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), isDelete, messageId);
   }

   @Override
   protected String getPacketString() {
      return  super.getPacketString() +
         "messageId=" + messageId + ", isDelete=" + isDelete;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof ReplicationLargeMessageEndMessage other)) {
         return false;
      }

      return messageId == other.messageId &&
             isDelete == other.isDelete;
   }

   public long getPendingRecordId() {
      return pendingRecordId;
   }

   public boolean isDelete() {
      return isDelete;
   }
}
