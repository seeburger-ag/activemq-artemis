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
package org.apache.activemq.artemis.utils;

import java.util.Locale;

public class CallerStack {
   /**
    * My package.
    */
   private static final String myPackage = "org.apache.activemq.artemis.utils"; // = CallerStack.class.getPackage().getName()

   /**
    * Unknown.
    */
   private static final String UNKNOWN = "Unknown";


   private CallerStack() {
       //
   }

   /**
    * Extracts information about the calling class.
    * Returns <code>"Unknown"</code> if information could not be obtained.
    * Will never return null.
    *
    * @param packageToIgnore - package name to ignore (startsWith comparison)
    * @return information about the calling class; <code>"Unknown"</code> if information could not be obtained.
    */
   public static String getCallerInfo(String packageToIgnore) {
       // make sure our caller does not pass junk
      if (packageToIgnore == null || packageToIgnore.isEmpty()) {
         packageToIgnore = myPackage;
      }
      packageToIgnore = packageToIgnore.toLowerCase();

      // get info about calling Class
      StackTraceElement[] stackTraceElements = new Throwable().getStackTrace();

      StringBuilder callerInfo = null;
      for (StackTraceElement element : stackTraceElements) {
         // get 1st element not contained in myPackage and not contained in passed packageToIgnore
         final String className = element.getClassName().toLowerCase(Locale.ROOT);
         if (callerInfo == null) {
            if (!(className.startsWith(myPackage)) && !(className.startsWith(packageToIgnore)) &&
                       !(className.startsWith("sun.reflect.")) &&
                       !(className.startsWith("java.lang.reflect."))) { // ignore Reflection classes by default
               callerInfo = new StringBuilder(element.toString());
            }
         } else {
               // append found SEEBURGER classes if not already present
            if (className.contains("seeburger")) {
               callerInfo.append(" - ").append(element.toString());
            }
         }
      }

      if (callerInfo == null) {
         return UNKNOWN;
      }
      return callerInfo.toString();
   }

}
