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
package org.apache.activemq.artemis.core.management.impl.view.predicate;

import org.apache.activemq.artemis.core.management.impl.view.SessionField;
import org.apache.activemq.artemis.core.server.ServerSession;

public class SessionFilterPredicate extends ActiveMQFilterPredicate<ServerSession> {

   private SessionField f;

   public SessionFilterPredicate() {
      super();
   }

   @Override
   public boolean test(ServerSession session) {
      // Using switch over enum vs string comparison is better for perf.
      if (f == null)
         return true;
      return switch (f) {
         case ID -> matches(session.getName());
         case CONNECTION_ID -> matches(session.getConnectionID());
         case CONSUMER_COUNT -> matches(session.getServerConsumers().size());
         case PRODUCER_COUNT -> matches(session.getServerProducers().size());
         case PROTOCOL -> matches(session.getRemotingConnection().getProtocolName());
         case CLIENT_ID -> matches(session.getRemotingConnection().getClientID());
         case LOCAL_ADDRESS -> matches(session.getRemotingConnection().getTransportConnection().getLocalAddress());
         case REMOTE_ADDRESS -> matches(session.getRemotingConnection().getTransportConnection().getRemoteAddress());
         default -> true;
      };
   }

   @Override
   public void setField(String field) {
      if (field != null && !field.isEmpty()) {
         this.f = SessionField.valueOfName(field);

         //for backward compatibility
         if (this.f == null) {
            this.f = SessionField.valueOf(field);
         }
      }
   }
}
