/*
 * ClusterHealthcheck.java
 *
 * created at 2023-07-29 by f.gervasi <YOURMAILADDRESS>
 *
 * Copyright (c) SEEBURGER AG, Germany. All Rights Reserved.
 */
package org.apache.activemq.artemis.core.server.seeburger.healthcheck;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.io.datastore.DataStoreSequentialFileFactory;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.persistence.AddressBindingInfo;
import org.apache.activemq.artemis.core.persistence.GroupingInfo;
import org.apache.activemq.artemis.core.persistence.QueueBindingInfo;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.PageCountPending;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.JournalLoader;
import org.apache.activemq.artemis.core.transaction.ResourceManager;


public class ClusterHealthcheck implements Runnable
{
    private ActiveMQServer server;
    private StorageManager storageManager;
    private JournalLoader loader;
    private PostOffice postOffice;
    private PagingManager pagingManager;
    private ResourceManager resourceManager;

    public ClusterHealthcheck(ActiveMQServer server, StorageManager storageManager, JournalLoader loader, PostOffice postOffice,
                              PagingManager pagingManager, ResourceManager resourceManager)
    {
        this.server = server;
        this.storageManager = storageManager;
        this.loader = loader;
        this.postOffice = postOffice;
        this.pagingManager = pagingManager;
        this.resourceManager = resourceManager;
    }


    @Override
    public void run() // start this inside initialisePart2 after own journal is loaded
    {
        while (server.isStarted())
        {
            // start discovery of process engines and their instance IDs
            // try to get locks on resources with those IDs and of type "messaging"
            // if a lock is acquired/an outage is detected
            // read file IDs from this instance from database
            // use StorageManager and JournalLoader to load foreign journals into own PostOffice
            try
            {
                List<QueueBindingInfo> queueBindings = new ArrayList<>();
                List<AddressBindingInfo> addressBindings = new ArrayList<>();
                List<GroupingInfo> groupings = new ArrayList<>();
                Map<Long, QueueBindingInfo> queueBindingsMap = new HashMap<>();

                DataStoreSequentialFileFactory factory = (DataStoreSequentialFileFactory)storageManager.getJournalSequentialFileFactory();
                factory.setInstance("instanceId"); // the instance which failed
                storageManager.loadBindingJournal(queueBindings, groupings, addressBindings);
                loader.initAddresses(addressBindings);
                loader.initQueues(queueBindingsMap, queueBindings);

                Map<SimpleString, List<Pair<byte[], Long>>> duplicateIDMap = new HashMap<>();
                HashSet<Pair<Long, Long>> pendingLargeMessages = new HashSet<>();
                List<PageCountPending> pendingNonTXPageCounter = new LinkedList<>();
                Set<Long> largeMessagesInFolder = new HashSet<>();
                storageManager.loadMessageJournal(postOffice, pagingManager, resourceManager, queueBindingsMap, duplicateIDMap,
                                                  pendingLargeMessages, largeMessagesInFolder, pendingNonTXPageCounter, loader);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }
}
