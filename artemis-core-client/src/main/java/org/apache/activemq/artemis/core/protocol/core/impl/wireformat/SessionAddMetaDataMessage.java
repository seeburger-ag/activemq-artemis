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

/**
 * A SessionAddMetaDataMessage
 * <p>
 * Packet deprecated: It exists only to support old formats
 */
public class SessionAddMetaDataMessage extends PacketImpl {

   private String key;
   private String data;

   public SessionAddMetaDataMessage() {
      super(SESS_ADD_METADATA);
   }

   public SessionAddMetaDataMessage(String k, String d) {
      this();
      key = k;
      data = d;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeString(key);
      buffer.writeString(data);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      key = buffer.readString();
      data = buffer.readString();
   }

   @Override
   public final boolean isRequiresConfirmations() {
      return false;
   }

   public String getKey() {
      return key;
   }

   public String getData() {
      return data;
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), data, key);
   }

   @Override
   protected String getPacketString() {
      StringBuilder sb = new StringBuilder(super.getPacketString());
      sb.append(", key=" + key);
      sb.append(", data=" + data);
      return sb.toString();
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof SessionAddMetaDataMessage other)) {
         return false;
      }

      return Objects.equals(data, other.data) &&
             Objects.equals(key, other.key);
   }
}
