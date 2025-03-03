/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.transport.amqp.client.sasl;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.security.sasl.SaslException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Implements the SASL PLAIN authentication Mechanism.
 *
 * User name and Password values are sent without being encrypted.
 */
public class CramMD5Mechanism extends AbstractMechanism {

   private static final String HMACMD5 = "HMACMD5";
   private boolean sentResponse;

   @Override
   public int getPriority() {
      return PRIORITY.HIGH.getValue();
   }

   @Override
   public String getName() {
      return "CRAM-MD5";
   }

   @Override
   public byte[] getInitialResponse() {
      return EMPTY;
   }

   @Override
   public byte[] getChallengeResponse(byte[] challenge) throws SaslException {
      if (!sentResponse && challenge != null && challenge.length != 0) {
         try {
            SecretKeySpec key = new SecretKeySpec(getPassword().getBytes(StandardCharsets.US_ASCII), HMACMD5);
            Mac mac = Mac.getInstance(HMACMD5);
            mac.init(key);

            byte[] bytes = mac.doFinal(challenge);

            StringBuilder hash = new StringBuilder(getUsername());
            hash.append(' ');
            for (byte b : bytes) {
               String hex = Integer.toHexString(0xFF & b);
               if (hex.length() == 1) {
                  hash.append('0');
               }
               hash.append(hex);
            }

            sentResponse = true;
            return hash.toString().getBytes(StandardCharsets.US_ASCII);
         } catch (InvalidKeyException e) {
            throw new SaslException("Unable to utilise key", e);
         } catch (NoSuchAlgorithmException e) {
            throw new SaslException("Unable to utilise required algorithm", e);
         }
      } else {
         return EMPTY;
      }
   }

   @Override
   public boolean isApplicable(String username, String password) {
      return username != null && !username.isEmpty() && password != null && !password.isEmpty();
   }
}
