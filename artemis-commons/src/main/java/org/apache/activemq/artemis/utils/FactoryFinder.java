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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class FactoryFinder {

   /**
    * The strategy that the FactoryFinder uses to find load and instantiate Objects can be changed out by calling the
    * setObjectFactory method with a custom implementation of ObjectFactory.
    * <p>
    * The default ObjectFactory is typically changed out when running in a specialized container environment where
    * service discovery needs to be done via the container system.  For example, in an OSGi scenario.
    */
   public interface ObjectFactory {

      /**
       * Creates an {@code Object} based on the input
       *
       * @param path the full service path
       * @return {@code Object}
       * @throws IllegalAccessException illegal access
       * @throws InstantiationException on instantiation error
       * @throws IOException            On IO Error
       * @throws ClassNotFoundException On class not found error
       */
      Object create(String path) throws IllegalAccessException, InstantiationException, IOException, ClassNotFoundException;

   }

   /**
    * The default implementation of Object factory which works well in standalone applications.
    */
   protected static class StandaloneObjectFactory implements ObjectFactory {

      final ConcurrentMap<String, Class> classMap = new ConcurrentHashMap<>();

      @Override
      public Object create(final String path) throws InstantiationException, IllegalAccessException, ClassNotFoundException, IOException {
         Class clazz = classMap.get(path);
         if (clazz == null) {
            clazz = loadClass(loadProperties(path));
            classMap.put(path, clazz);
         }
         try {
            return clazz.getDeclaredConstructor().newInstance();
         } catch (NoSuchMethodException | InvocationTargetException e) {
            throw new IOException(e);
         }
      }

      static Class loadClass(Properties properties) throws ClassNotFoundException, IOException {

         String className = properties.getProperty("class");
         if (className == null) {
            throw new IOException("Expected property is missing: class");
         }
         Class clazz = null;
         ClassLoader loader = Thread.currentThread().getContextClassLoader();
         if (loader != null) {
            try {
               clazz = loader.loadClass(className);
            } catch (ClassNotFoundException e) {
               // ignore
            }
         }
         if (clazz == null) {
            clazz = FactoryFinder.class.getClassLoader().loadClass(className);
         }

         return clazz;
      }

      public Properties loadProperties(String uri) throws IOException {
         // lets try the thread context class loader first
         ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
         if (classLoader == null) {
            classLoader = StandaloneObjectFactory.class.getClassLoader();
         }
         InputStream in = classLoader.getResourceAsStream(uri);
         if (in == null) {
            in = FactoryFinder.class.getClassLoader().getResourceAsStream(uri);
            if (in == null) {
               throw new IOException("Could not find factory class for resource: " + uri);
            }
         }

         // lets load the file
         try (BufferedInputStream reader = new BufferedInputStream(in)) {
            Properties properties = new Properties();
            properties.load(reader);
            return properties;
         }
      }
   }

   // ================================================================
   // Class methods and properties
   // ================================================================
   private static ObjectFactory objectFactory = new StandaloneObjectFactory();

   public static ObjectFactory getObjectFactory() {
      return objectFactory;
   }

   public static void setObjectFactory(ObjectFactory objectFactory) {
      FactoryFinder.objectFactory = objectFactory;
   }

   // ================================================================
   // Instance methods and properties
   // ================================================================
   private final String path;

   public FactoryFinder(String path) {
      this.path = path;
   }

   /**
    * Creates a new instance of the given key
    *
    * @param key is the key to add to the path to find a text file containing the factory name
    * @return a newly created instance
    * @throws IllegalAccessException On illegal access
    * @throws InstantiationException On can not instantiate exception
    * @throws IOException            On IOException
    * @throws ClassNotFoundException When class not on class path
    */
   public Object newInstance(String key) throws IllegalAccessException, InstantiationException, IOException, ClassNotFoundException {
      return objectFactory.create(path + key);
   }
}
