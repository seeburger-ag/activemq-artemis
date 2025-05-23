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
package org.apache.activemq;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.util.concurrent.TimeUnit;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.activemq.util.Wait;

public class JmsRedeliveredTest extends TestCase {

   private Connection connection;

   @Override
   protected void setUp() throws Exception {
      connection = createConnection();
   }

   @Override
   protected void tearDown() throws Exception {
      if (connection != null) {
         connection.close();
         connection = null;
      }
      CombinationTestSupport.checkStopped();
   }

   protected Connection createConnection() throws Exception {
      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
      return factory.createConnection();
   }

   /*
    * Tests if a message unacknowledged message gets to be resent when the
    * session is closed and then a new consumer session is created.
    */
   public void testQueueSessionCloseMarksMessageRedelivered() throws JMSException {
      connection.start();

      Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      Queue queue = session.createQueue("queue-" + getName());
      MessageProducer producer = createProducer(session, queue);
      producer.send(createTextMessage(session));

      // Consume the message...
      MessageConsumer consumer = session.createConsumer(queue);
      Message msg = consumer.receive(1000);
      assertNotNull(msg);
      assertFalse("Message should not be redelivered.", msg.getJMSRedelivered());
      // Don't ack the message.

      // Reset the session. This should cause the Unacked message to be
      // redelivered.
      session.close();
      session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

      // Attempt to Consume the message...
      consumer = session.createConsumer(queue);
      msg = consumer.receive(2000);
      assertNotNull(msg);
      assertTrue("Message should be redelivered.", msg.getJMSRedelivered());
      msg.acknowledge();

      session.close();
   }

   public void testQueueSessionCloseMarksUnAckedMessageRedelivered() throws JMSException {
      connection.start();

      Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      Queue queue = session.createQueue("queue-" + getName());
      MessageProducer producer = createProducer(session, queue);
      producer.send(createTextMessage(session, "1"));
      producer.send(createTextMessage(session, "2"));

      // Consume the message...
      MessageConsumer consumer = session.createConsumer(queue);
      Message msg = consumer.receive(1000);
      assertNotNull(msg);
      assertFalse("Message should not be redelivered.", msg.getJMSRedelivered());
      assertEquals("1", ((TextMessage) msg).getText());
      msg.acknowledge();

      // Don't ack the message.
      msg = consumer.receive(1000);
      assertNotNull(msg);
      assertFalse("Message should not be redelivered.", msg.getJMSRedelivered());
      assertEquals("2", ((TextMessage) msg).getText());

      // Reset the session. This should cause the Unacked message to be
      // redelivered.
      session.close();
      session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

      // Attempt to Consume the message...
      consumer = session.createConsumer(queue);
      msg = consumer.receive(2000);
      assertNotNull(msg);
      assertEquals("2", ((TextMessage) msg).getText());
      assertTrue("Message should be redelivered.", msg.getJMSRedelivered());
      msg.acknowledge();

      session.close();
   }

   /*
    * Tests session recovery and that the redelivered message is marked as
    * such. Session uses client acknowledgement, the destination is a queue.
    */
   public void testQueueRecoverMarksMessageRedelivered() throws JMSException {
      connection.start();

      Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      Queue queue = session.createQueue("queue-" + getName());
      MessageProducer producer = createProducer(session, queue);
      producer.send(createTextMessage(session));

      // Consume the message...
      MessageConsumer consumer = session.createConsumer(queue);
      Message msg = consumer.receive(1000);
      assertNotNull(msg);
      assertFalse("Message should not be redelivered.", msg.getJMSRedelivered());
      // Don't ack the message.

      // Reset the session. This should cause the Unacked message to be
      // redelivered.
      session.recover();

      // Attempt to Consume the message...
      msg = consumer.receive(2000);
      assertNotNull(msg);
      assertTrue("Message should be redelivered.", msg.getJMSRedelivered());
      msg.acknowledge();

      session.close();
   }

   /*
    * Tests rollback message to be marked as redelivered. Session uses client
    * acknowledgement and the destination is a queue.
    */
   public void testQueueRollbackMarksMessageRedelivered() throws JMSException {
      connection.start();

      Session session = connection.createSession(true, Session.CLIENT_ACKNOWLEDGE);
      Queue queue = session.createQueue("queue-" + getName());
      MessageProducer producer = createProducer(session, queue);
      producer.send(createTextMessage(session));
      session.commit();

      // Get the message... Should not be redelivered.
      MessageConsumer consumer = session.createConsumer(queue);
      Message msg = consumer.receive(1000);
      assertNotNull(msg);
      assertFalse("Message should not be redelivered.", msg.getJMSRedelivered());

      // Rollback.. should cause redelivery.
      session.rollback();

      // Attempt to Consume the message...
      msg = consumer.receive(2000);
      assertNotNull(msg);
      assertTrue("Message should be redelivered.", msg.getJMSRedelivered());

      session.commit();
      session.close();
   }

   /*
    * Tests if the message gets to be re-delivered when the session closes and
    * that the re-delivered message is marked as such. Session uses client
    * acknowledgment, the destination is a topic and the consumer is a durable
    * subscriber.
    */
   public void testDurableTopicSessionCloseMarksMessageRedelivered() throws JMSException {
      connection.setClientID(getName());
      connection.start();

      Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      Topic topic = session.createTopic("topic-" + getName());
      MessageConsumer consumer = session.createDurableSubscriber(topic, "sub1");

      // This case only works with persistent messages since transient
      // messages
      // are dropped when the consumer goes offline.
      MessageProducer producer = session.createProducer(topic);
      producer.setDeliveryMode(DeliveryMode.PERSISTENT);
      producer.send(createTextMessage(session));

      // Consume the message...
      Message msg = consumer.receive(1000);
      assertNotNull(msg);
      assertFalse("Message should not be re-delivered.", msg.getJMSRedelivered());
      // Don't ack the message.

      // Reset the session. This should cause the Unacked message to be
      // re-delivered.
      session.close();
      session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

      // Attempt to Consume the message...
      consumer = session.createDurableSubscriber(topic, "sub1");
      msg = consumer.receive(2000);
      assertNotNull(msg);
      assertTrue("Message should be redelivered.", msg.getJMSRedelivered());
      msg.acknowledge();

      session.close();
   }

   /*
    * Tests session recovery and that the redelivered message is marked as
    * such. Session uses client acknowledgement, the destination is a topic and
    * the consumer is a durable suscriber.
    */
   public void testDurableTopicRecoverMarksMessageRedelivered() throws JMSException {
      connection.setClientID(getName());
      connection.start();

      Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      Topic topic = session.createTopic("topic-" + getName());
      MessageConsumer consumer = session.createDurableSubscriber(topic, "sub1");

      MessageProducer producer = createProducer(session, topic);
      producer.send(createTextMessage(session));

      // Consume the message...
      Message msg = consumer.receive(1000);
      assertNotNull(msg);
      assertFalse("Message should not be redelivered.", msg.getJMSRedelivered());
      // Don't ack the message.

      // Reset the session. This should cause the Unacked message to be
      // redelivered.
      session.recover();

      // Attempt to Consume the message...
      msg = consumer.receive(2000);
      assertNotNull(msg);
      assertTrue("Message should be redelivered.", msg.getJMSRedelivered());
      msg.acknowledge();

      session.close();
   }

   /*
    * Tests rollback message to be marked as redelivered. Session uses client
    * acknowledgement and the destination is a topic.
    */
   public void testDurableTopicRollbackMarksMessageRedelivered() throws JMSException {
      connection.setClientID(getName());
      connection.start();

      Session session = connection.createSession(true, Session.CLIENT_ACKNOWLEDGE);
      Topic topic = session.createTopic("topic-" + getName());
      MessageConsumer consumer = session.createDurableSubscriber(topic, "sub1");

      MessageProducer producer = createProducer(session, topic);
      producer.send(createTextMessage(session));
      session.commit();

      // Get the message... Should not be redelivered.
      Message msg = consumer.receive(1000);
      assertNotNull(msg);
      assertFalse("Message should not be redelivered.", msg.getJMSRedelivered());

      // Rollback.. should cause redelivery.
      session.rollback();

      // Attempt to Consume the message...
      msg = consumer.receive(2000);
      assertNotNull(msg);
      assertTrue("Message should be redelivered.", msg.getJMSRedelivered());

      session.commit();
      session.close();
   }

   public void testTopicRecoverMarksMessageRedelivered() throws JMSException {

      connection.setClientID(getName());
      connection.start();

      Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      Topic topic = session.createTopic("topic-" + getName());
      MessageConsumer consumer = session.createConsumer(topic);

      MessageProducer producer = createProducer(session, topic);
      producer.send(createTextMessage(session));

      // Consume the message...
      Message msg = consumer.receive(1000);
      assertNotNull(msg);
      assertFalse("Message should not be redelivered.", msg.getJMSRedelivered());
      // Don't ack the message.

      // Reset the session. This should cause the Unacked message to be
      // redelivered.
      session.recover();

      // Attempt to Consume the message...
      msg = consumer.receive(2000);
      assertNotNull(msg);
      assertTrue("Message should be redelivered.", msg.getJMSRedelivered());
      msg.acknowledge();

      session.close();
   }

   /*
    * Tests rollback message to be marked as redelivered. Session uses client
    * acknowledgement and the destination is a topic.
    */
   public void testTopicRollbackMarksMessageRedelivered() throws JMSException {
      connection.setClientID(getName());
      connection.start();

      Session session = connection.createSession(true, Session.CLIENT_ACKNOWLEDGE);
      Topic topic = session.createTopic("topic-" + getName());
      MessageConsumer consumer = session.createConsumer(topic);

      MessageProducer producer = createProducer(session, topic);
      producer.send(createTextMessage(session));
      session.commit();

      // Get the message... Should not be redelivered.
      Message msg = consumer.receive(1000);
      assertNotNull(msg);
      assertFalse("Message should not be redelivered.", msg.getJMSRedelivered());

      // Rollback.. should cause redelivery.
      session.rollback();

      // Attempt to Consume the message...
      msg = consumer.receive(2000);
      assertNotNull(msg);
      assertTrue("Message should be redelivered.", msg.getJMSRedelivered());

      session.commit();
      session.close();
   }

   public void testNoReceiveConsumerDisconnectDoesNotIncrementRedelivery() throws Exception {
      connection.setClientID(getName());
      connection.start();

      Connection keepBrokerAliveConnection = createConnection();
      keepBrokerAliveConnection.start();

      Session session = connection.createSession(true, Session.CLIENT_ACKNOWLEDGE);
      Queue queue = session.createQueue("queue-" + getName());
      final MessageConsumer consumer = session.createConsumer(queue);

      MessageProducer producer = createProducer(session, queue);
      producer.send(createTextMessage(session));
      session.commit();

      Wait.waitFor(() -> ((ActiveMQMessageConsumer) consumer).getMessageSize() == 1);

      connection.close();

      session = keepBrokerAliveConnection.createSession(true, Session.CLIENT_ACKNOWLEDGE);
      MessageConsumer messageConsumer = session.createConsumer(queue);
      Message msg = messageConsumer.receive(1000);
      assertNotNull(msg);
      msg.acknowledge();

      assertFalse("Message should not be redelivered.", msg.getJMSRedelivered());
      session.commit();
      session.close();
      keepBrokerAliveConnection.close();
   }

   public void testNoReceiveConsumerDoesNotIncrementRedelivery() throws Exception {
      connection.setClientID(getName());
      connection.start();

      Session session = connection.createSession(true, Session.CLIENT_ACKNOWLEDGE);
      Queue queue = session.createQueue("queue-" + getName());
      MessageConsumer consumer = session.createConsumer(queue);

      MessageProducer producer = createProducer(session, queue);
      producer.send(createTextMessage(session));
      session.commit();

      TimeUnit.SECONDS.sleep(1);
      consumer.close();

      consumer = session.createConsumer(queue);
      Message msg = consumer.receive(1000);
      assertNotNull(msg);

      assertFalse("Message should not be redelivered.", msg.getJMSRedelivered());
      session.commit();
      session.close();
   }

   public void testNoReceiveDurableConsumerDoesNotIncrementRedelivery() throws Exception {
      connection.setClientID(getName());
      connection.start();

      Session session = connection.createSession(true, Session.CLIENT_ACKNOWLEDGE);
      Topic topic = session.createTopic("topic-" + getName());
      MessageConsumer consumer = session.createDurableSubscriber(topic, "sub");

      MessageProducer producer = createProducer(session, topic);
      producer.send(createTextMessage(session));
      session.commit();

      TimeUnit.SECONDS.sleep(1);
      consumer.close();

      consumer = session.createDurableSubscriber(topic, "sub");
      Message msg = consumer.receive(1000);
      assertNotNull(msg);

      assertFalse("Message should not be redelivered.", msg.getJMSRedelivered());

      session.commit();
      session.close();
   }

   private TextMessage createTextMessage(Session session) throws JMSException {
      return createTextMessage(session, "Hello");
   }

   private TextMessage createTextMessage(Session session, String txt) throws JMSException {
      return session.createTextMessage(txt);
   }

   private MessageProducer createProducer(Session session, Destination queue) throws JMSException {
      MessageProducer producer = session.createProducer(queue);
      producer.setDeliveryMode(getDeliveryMode());
      return producer;
   }

   protected int getDeliveryMode() {
      return DeliveryMode.PERSISTENT;
   }

   public static final class PersistentCase extends JmsRedeliveredTest {
      @Override
      protected int getDeliveryMode() {
         return DeliveryMode.PERSISTENT;
      }
   }

   public static final class TransientCase extends JmsRedeliveredTest {
      @Override
      protected int getDeliveryMode() {
         return DeliveryMode.NON_PERSISTENT;
      }
   }

   public static Test suite() {
      TestSuite suite = new TestSuite();
      suite.addTestSuite(PersistentCase.class);
      suite.addTestSuite(TransientCase.class);
      return suite;
   }
}
