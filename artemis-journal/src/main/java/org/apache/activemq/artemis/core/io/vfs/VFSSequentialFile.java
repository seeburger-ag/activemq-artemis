/*
 * VFSSequentialFile.java
 *
 * created at 2023-08-29 by f.gervasi f.gervasi@seeburger.de
 *
 * Copyright (c) SEEBURGER AG, Germany. All Rights Reserved.
 */
package org.apache.activemq.artemis.core.io.vfs;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.io.DummyCallback;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.buffer.TimedBuffer;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.core.journal.impl.SimpleWaitIOCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.seeburger.vfs.application.client.api.VFSClientService;
import com.seeburger.vfs.application.client.api.context.VFSContext;
import com.seeburger.vfs.application.client.api.model.VFSFile;
import com.seeburger.vfs.application.client.api.model.VFSFileOperationMode;
import com.seeburger.vfs.client.api.VFSException;
import com.seeburger.recover.impl.vfs.VFSClientServiceFactory;
import com.seeburger.seeauth.LoginSession;


public class VFSSequentialFile implements SequentialFile
{
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private String fileName;
    private String journalDir;
    private VFSSequentialFileFactory factory;
    private VFSClientService vfsClient;
    private CountDownLatch vfsClientFinished = new CountDownLatch(1);
    private ByteArrayOutputStream tempData = new ByteArrayOutputStream();
    private boolean isOpen = false;
    private OutputStream fileOut;
    private WritableByteChannel fileChannel;
    private WritableByteChannel tempChannel;

    public VFSSequentialFile(String fileName, String journalDir, VFSSequentialFileFactory factory)
    {
        this.fileName = fileName;
        this.journalDir = journalDir;
        this.factory = factory;
        tempChannel = Channels.newChannel(tempData);
        new Thread(tryAndGetVFSClient).start();
    }

    private Runnable tryAndGetVFSClient = () ->
    {
        try
        {
            LoginSession.createSession(LoginSession.LS_ALL, null);
            VFSContext.enterVFSContext("VFSJournal");
            this.vfsClient = VFSClientServiceFactory.getVFSClientService();
            VFSFile file = null;
            while (file == null)
            {
                file = tryToSetFile();
            }
            fileOut = file.getOutputStream(VFSFileOperationMode.APPEND);
            fileChannel = Channels.newChannel(fileOut);
            fileOut.write(tempData.toByteArray());
            tempChannel.close();
            vfsClientFinished.countDown();
        }
        catch (IllegalStateException | VFSException | IOException e)
        {
            LOG.error("Waiting for VFS to become available took too long.", e);
        }
    };

    private VFSFile tryToSetFile()
    {
        VFSFile file = null;
        try
        {
            if (!vfsClient.existsFile(Paths.get(journalDir, fileName)))
            {
                file = vfsClient.createFile(Paths.get(journalDir, fileName));
            }
            else
            {
                file = vfsClient.getFile(Paths.get(journalDir, fileName));
            }
        }
        catch (Exception e)
        {
            try
            {
                LOG.info("Will sleep 5 seconds.");
                Thread.sleep(5000);
            }
            catch (InterruptedException e1)
            {
                Thread.currentThread().interrupt();
            }
        }
        return file;
    }


    @Override
    public boolean isOpen()
    {
        return isOpen;
    }


    @Override
    public boolean exists()
    {
        if (vfsClientFinished.getCount() == 0)
        {
            try
            {
                return vfsClient.existsFile(Paths.get(journalDir, fileName));
            }
            catch (VFSException e)
            {
                LOG.error(e.getMessage());
            }
        }
        return false;
    }


    @Override
    public void open()
        throws Exception
    {
        open(1, false);
    }


    @Override
    public void open(int maxIO, boolean useExecutor)
        throws Exception
    {
        if (vfsClientFinished.getCount() == 0)
        {
            if (!exists())
            {
                vfsClient.createFile(Paths.get(journalDir, fileName));
            }
            fileOut = vfsClient.getFile(Paths.get(journalDir, fileName)).getOutputStream(VFSFileOperationMode.APPEND);
        }
        isOpen = true;
    }


    @Override
    public ByteBuffer map(int position, long size)
        throws IOException
    {
        return null;
    }


    @Override
    public boolean fits(int size)
    {
        return true;
    }


    @Override
    public int calculateBlockStart(int position)
        throws Exception
    {
        return position;
    }


    @Override
    public String getFileName()
    {
        return fileName;
    }


    @Override
    public void fill(int size)
        throws Exception
    {
        // Nothing to do here
    }


    @Override
    public void delete()
        throws IOException, InterruptedException, ActiveMQException
    {
        if (vfsClientFinished.getCount() == 0)
        {
            try
            {
                vfsClient.delete(Paths.get(journalDir, fileName), false);
            }
            catch (VFSException e)
            {
                LOG.error(e.getMessage());
            }
        }
    }


    @Override
    public void write(ActiveMQBuffer bytes, boolean sync, IOCallback callback)
        throws Exception
    {
        int readableBytes = bytes.readableBytes();
        ByteBuffer buffer = factory.newBuffer(readableBytes);
        buffer.limit(readableBytes);
        bytes.getBytes(bytes.readerIndex(), buffer);
        buffer.flip();
        writeDirect(buffer, sync, callback);
    }


    @Override
    public void write(ActiveMQBuffer bytes, boolean sync)
        throws Exception
    {
        if (sync)
        {
            SimpleWaitIOCallback completion = new SimpleWaitIOCallback();
            write(bytes, true, completion);
            completion.waitCompletion();
        }
        else
        {
            write(bytes, false, DummyCallback.getInstance());
        }
    }


    @Override
    public void write(EncodingSupport bytes, boolean sync, IOCallback callback)
        throws Exception
    {
        int encodedSize = bytes.getEncodeSize();
        ByteBuffer buffer = factory.newBuffer(encodedSize);
        ActiveMQBuffer outBuffer = ActiveMQBuffers.wrappedBuffer(buffer);
        bytes.encode(outBuffer);
        buffer.clear();
        buffer.limit(encodedSize);
        writeDirect(buffer, sync, callback);
    }


    @Override
    public void write(EncodingSupport bytes, boolean sync)
        throws Exception
    {
        if (sync)
        {
            SimpleWaitIOCallback completion = new SimpleWaitIOCallback();
            write(bytes, true, completion);
            completion.waitCompletion();
        }
        else
        {
            write(bytes, false, DummyCallback.getInstance());
        }
    }


    @Override
    public void writeDirect(ByteBuffer bytes, boolean sync, IOCallback callback)
    {
        internalWrite(bytes, sync, callback, true);
    }


    @Override
    public void writeDirect(ByteBuffer bytes, boolean sync)
        throws Exception
    {
        internalWrite(bytes, sync, null, true);
    }


    @Override
    public void blockingWriteDirect(ByteBuffer bytes, boolean sync, boolean releaseBuffer)
        throws Exception
    {
        internalWrite(bytes, sync, null, releaseBuffer);
    }


    @Override
    public int read(ByteBuffer bytes, IOCallback callback)
        throws Exception
    {
        if (vfsClientFinished.getCount() != 0)
        {
            LOG.error("Cannot read file because VFS Client is not available yet.");
            return -1;
        }
        VFSFile file = vfsClient.getFile(Paths.get(journalDir, fileName));
        InputStream fileIn = file.getInputStream();
        int bytesRead = 0;
        if (bytes.hasArray())
        {
            bytesRead = fileIn.read(bytes.array(), bytes.arrayOffset() + bytes.remaining(), bytes.limit());
        }
        callback.done();
        return bytesRead;
    }


    @Override
    public int read(ByteBuffer bytes)
        throws Exception
    {
        return read(bytes, DummyCallback.getInstance());
    }


    @Override
    public void position(long pos)
        throws IOException
    {
        // Not needed right now
    }


    @Override
    public long position()
    {
        return 0;
    }


    @Override
    public void close()
        throws Exception
    {
        if (vfsClientFinished.getCount() == 0)
        {
            fileChannel.close();
        }
        isOpen = false;
    }


    @Override
    public void sync()
        throws IOException
    {
        LoginSession.createSession(LoginSession.LS_ALL, null);
        VFSContext.enterVFSContext("VFSJournal");
        fileChannel.close();
        try
        {
            fileOut = vfsClient.getFile(Paths.get(journalDir, fileName)).getOutputStream(VFSFileOperationMode.APPEND);
            fileChannel = Channels.newChannel(fileOut);
        }
        catch (VFSException e)
        {
            LOG.error("Could not get OutputStream for file!", e);
        }
    }


    @Override
    public long size()
        throws Exception
    {
        if (vfsClientFinished.getCount() == 0)
        {
            return vfsClient.getFile(Paths.get(journalDir, fileName)).getSize();
        }
        return 0;
    }


    @Override
    public void renameTo(String newFileName)
        throws Exception
    {
        if (vfsClientFinished.getCount() == 0)
        {
            VFSFile oldFile = vfsClient.getFile(Paths.get(journalDir, fileName));
            VFSFile newFile = vfsClient.createFile(Paths.get(journalDir, newFileName));
            OutputStream newFileOut = newFile.getOutputStream();
            InputStream oldFileIn = oldFile.getInputStream();
            byte[] allBytes = oldFileIn.readAllBytes();
            newFileOut.write(allBytes);
            newFileOut.close();
            oldFileIn.close();
            vfsClient.delete(Paths.get(journalDir, fileName), false);
            fileName = newFileName;
        }
    }


    @Override
    public SequentialFile cloneFile()
    {
        return new VFSSequentialFile(fileName, journalDir, factory);
    }


    @Override
    public void copyTo(SequentialFile newFileName)
        throws Exception
    {
        if (vfsClientFinished.getCount() == 0)
        {
            vfsClient.move(Paths.get(journalDir, fileName), Paths.get(journalDir, newFileName.getFileName()));
        }
    }


    @Override
    public void setTimedBuffer(TimedBuffer buffer)
    {
        // Do nothing
    }


    @Override
    public File getJavaFile()
    {
        return null;
    }


    private void internalWrite(ByteBuffer bytes, boolean sync, IOCallback callback, boolean releaseBuffer)
    {
        try
        {
            if (vfsClientFinished.getCount() == 0)
            {
                internalWriteWithFileStreamData(bytes);
            }
            else
            {
                internalWriteWithTempStreamData(bytes);
            }

            if (sync && vfsClientFinished.getCount() == 0)
            {
                sync();
            }

            if (callback != null)
            {
                callback.done();
            }
        }
        catch (IOException e)
        {
            LOG.error(e.getMessage());
        }
        finally
        {
            if (releaseBuffer)
            {
                factory.releaseBuffer(bytes);
            }
        }
    }


    private void internalWriteWithTempStreamData(ByteBuffer buffer)
        throws IOException
    {
        if (buffer.hasArray())
        {
            tempData.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
        }
        else
        {
            tempChannel.write(buffer);
        }
    }


    private void internalWriteWithFileStreamData(ByteBuffer buffer)
        throws IOException
    {
        if (buffer.hasArray())
        {
            fileOut.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
        }
        else
        {
            fileChannel.write(buffer);
        }
    }


    private <T> T measureTime(Callable<T> call, String name)
    {
        try
        {
            long start = System.currentTimeMillis();
            T value = call.call();
            long delta = System.currentTimeMillis() - start;
            LOG.info("Call to {} took {} ms.", name, delta);
            return value;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }

}
