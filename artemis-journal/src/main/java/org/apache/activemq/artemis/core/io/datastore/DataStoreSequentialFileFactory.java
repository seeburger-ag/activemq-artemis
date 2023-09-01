/*
 * DataStoreSequentialFileFactory.java
 *
 * created at 2023-07-17 by f.gervasi f.gervasi@seeburger.de
 *
 * Copyright (c) SEEBURGER AG, Germany. All Rights Reserved.
 */
package org.apache.activemq.artemis.core.io.datastore;


import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.activemq.artemis.core.io.AbstractSequentialFileFactory;
import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzer;

import com.seeburger.storage.common.util.StorageUtil;

import io.netty.util.internal.PlatformDependent;


public class DataStoreSequentialFileFactory extends AbstractSequentialFileFactory
{
    private Path mapping;
    private String instanceId; // default is own instance ID
    private Map<String, String> createdFiles = new HashMap<>();

    protected DataStoreSequentialFileFactory(File journalDir, boolean buffered, int bufferSize, int bufferTimeout, int maxIO,
                                             boolean logRates, IOCriticalErrorListener criticalErrorListener,
                                             CriticalAnalyzer criticalAnalyzer)
    {
        super(journalDir, buffered, bufferSize, bufferTimeout, maxIO, logRates, criticalErrorListener, criticalAnalyzer);
    }


    public DataStoreSequentialFileFactory(boolean logRates, IOCriticalErrorListener criticalErrorListener,
                                          CriticalAnalyzer criticalAnalyzer)
    {
        this(null, false, 0, 0, 1, logRates, criticalErrorListener, criticalAnalyzer);
//        mapping = Paths.get(System.getProperty("bisas.temp"), "datastore-mapping.txt");
//        createMappingFile();
    }


    @Override
    public SequentialFile createSequentialFile(String fileName)
    {
        // do not always create a new UUID!
        // look in the mapping table for the UUID mapped to this fileName
        String fileId = StorageUtil.createUUID();
//        if (!isMappingExistsForFileName(fileName))
//        {
//            fileId = StorageUtil.createUUID();
//            putNewFile(fileId, fileName);
//        }
//        else
//        {
//            fileId = getCreatedFiles().entrySet().stream().filter(entry -> entry.getValue().equals(fileName)).findFirst().get().getKey();
//        }
        createdFiles.put(fileId, fileName);
        return new DataStoreSequentialFile(fileName, fileId, this);
    }


    @Override
    public boolean isSupportsCallbacks()
    {
        return false;
    }


    @Override
    public ByteBuffer allocateDirectBuffer(int size)
    {
        return ByteBuffer.allocateDirect(size);
    }


    @Override
    public void releaseDirectBuffer(ByteBuffer buffer)
    {
        if (buffer.isDirect())
        {
            PlatformDependent.freeDirectBuffer(buffer);
        }
    }


    @Override
    public ByteBuffer newBuffer(int size)
    {
        return ByteBuffer.allocate(size);
    }


    @Override
    public ByteBuffer wrapBuffer(byte[] bytes)
    {
        return ByteBuffer.wrap(bytes);
    }


    @Override
    public int calculateBlockSize(int bytes)
    {
        return bytes;
    }


    @Override
    public void clearBuffer(ByteBuffer buffer)
    {
        if (buffer.isDirect())
        {
            PlatformDependent.setMemory(PlatformDependent.directBufferAddress(buffer), buffer.limit(), (byte)0);
        }
        else
        {
            Arrays.fill(buffer.array(), buffer.arrayOffset(), buffer.limit(), (byte)0);
        }
    }


    @Override
    public List<String> listFiles(String extension)
        throws Exception
    {
        // retrieve the files from the DataStore which are found in the mapping table of the instance with the instanceId
        // SELECT * FROM zuordung WHERE instance_id = this.instanceId AND file_name.endwith(extension)
//        return getCreatedFiles().values().stream().filter(name -> name.endsWith(extension)).collect(Collectors.toList());
        return Collections.emptyList();
    }


    public void deleteFileName(String fileId)
    {
//        try
//        {
//            List<String> lines = Files.readAllLines(mapping).stream().filter(line -> !line.startsWith(fileId)).collect(Collectors.toList());
//            Files.delete(mapping);
//            createMappingFile();
//            lines.stream().forEach(line ->
//            {
//                try
//                {
//                    Files.writeString(mapping, String.format("%s%n", line), StandardOpenOption.APPEND);
//                }
//                catch (IOException e)
//                {
//                    e.printStackTrace();
//                }
//            });
//        }
//        catch (IOException e)
//        {
//            e.printStackTrace();
//        }
        createdFiles.remove(fileId);
    }


    public void changeFileName(String newName, String fileId)
    {
//        deleteFileName(fileId);
//        putNewFile(fileId, newName);
        createdFiles.put(fileId, newName);
    }


    public void setInstance(String instanceId)
    {
        this.instanceId = instanceId;
    }


    public Map<String, String> getCreatedFiles()
    {
        Map<String, String> createdFiles = new HashMap<>();
        try
        {
            List<String> lines = Files.readAllLines(mapping);
            lines.stream().forEach(line ->
            {
                String fileId = line.split(":")[0];
                String fileName = line.split(":")[1];
                createdFiles.put(fileId, fileName);
            });
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return createdFiles;
    }


    private void putNewFile(String fileId, String fileName)
    {
        try
        {
            Files.writeString(mapping, String.format("%s:%s%n", fileId, fileName), StandardOpenOption.APPEND);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }


    private void createMappingFile()
    {
        if (Files.notExists(mapping))
        {
            try
            {
                Files.createFile(mapping);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }


    private boolean isMappingExistsForFileName(String fileName)
    {
        Map<String, String> mappings = getCreatedFiles();
        return mappings.containsValue(fileName);
    }
}
