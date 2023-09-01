/*
 * VFSSequentialFileFactory.java
 *
 * created at 2023-08-29 by f.gervasi f.gervasi@seeburger.de
 *
 * Copyright (c) SEEBURGER AG, Germany. All Rights Reserved.
 */
package org.apache.activemq.artemis.core.io.vfs;


import java.io.File;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.activemq.artemis.core.io.AbstractSequentialFileFactory;
import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.utils.Env;
import org.apache.activemq.artemis.utils.PowerOf2Util;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzer;

import io.netty.util.internal.PlatformDependent;


public class VFSSequentialFileFactory extends AbstractSequentialFileFactory
{

    public VFSSequentialFileFactory(File journalDir, boolean buffered, int bufferSize, int bufferTimeout, int maxIO, boolean logRates,
                                    IOCriticalErrorListener criticalErrorListener, CriticalAnalyzer criticalAnalyzer)
    {
        super(journalDir, buffered, bufferSize, bufferTimeout, maxIO, logRates, criticalErrorListener, criticalAnalyzer);
    }


    @Override
    public SequentialFile createSequentialFile(String fileName)
    {
        return new VFSSequentialFile(fileName, journalDir.getPath(), this);
    }


    @Override
    public boolean isSupportsCallbacks()
    {
        return false;
    }


    @Override
    public ByteBuffer allocateDirectBuffer(int size)
    {
        int requiredCapacity = PowerOf2Util.align(size, Env.osPageSize());
        ByteBuffer buffer = ByteBuffer.allocateDirect(requiredCapacity);
        buffer.limit(size);
        return buffer;
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
        return newBuffer(size, true);
    }


    @Override
    public ByteBuffer newBuffer(int size, boolean zeroed)
    {
        return allocateDirectBuffer(size);
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
        return Collections.emptyList();
    }

}
