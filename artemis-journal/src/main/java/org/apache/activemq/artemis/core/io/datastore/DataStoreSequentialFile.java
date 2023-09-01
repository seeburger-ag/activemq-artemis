/*
 * DataStoreSequentialFile.java
 *
 * created at 2023-07-17 by f.gervasi f.gervasi@seeburger.de
 *
 * Copyright (c) SEEBURGER AG, Germany. All Rights Reserved.
 */
package org.apache.activemq.artemis.core.io.datastore;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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

import com.seeburger.storage.api.Storage;
import com.seeburger.storage.api.StorageException;
import com.seeburger.storage.api.StorageFile;
import com.seeburger.storage.service.StorageService;


public class DataStoreSequentialFile implements SequentialFile
{
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private Storage storage;
    private StorageFile storageFile;
    private TimedBuffer timedBuffer;
    private AtomicBoolean isOpen = new AtomicBoolean(false);
    private AtomicLong readPosition = new AtomicLong(0);
    private final Object tempDataSemaphore = new Object();
    private boolean isAlreadyLookingForStorage = false;
    private OutputStream storageFileOut;
    private InputStream storageFileIn;
    private ByteArrayOutputStream tempData;
    private int bytesReceived;
    private int syncCallCounter;
    private List<String> filesToBeRemoved = new ArrayList<>();
    private Path fileSizeFile;
    private String fileName;
    private String fileId;
    private DataStoreSequentialFileFactory factory;

    public DataStoreSequentialFile(String fileName, String fileId, DataStoreSequentialFileFactory factory)
    {
        this.fileName = fileName;
        this.fileId = fileId;
        this.factory = factory;
        createFileSizeFile();
        if (parseFileSizeFile().get(fileId) == null)
        {
            writeSizeToFileSizeFile(0);
        }
    }


    @Override
    public boolean isOpen()
    {
        return isOpen.get();
    }


    @Override
    public boolean exists()
    {
        try
        {
            return storageFile != null && storage.isFileExist(storageFile.getId(), false);
        }
        catch (StorageException e)
        {
            LOG.error("Failed to check whether file exists or not.", e);
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
        LOG.info("Opened file {}", fileId);
        getStorage();
        if (storage != null)
        {
            if (!storage.isFileExist(fileId, false))
            {
                storageFile = storage.createFile(fileId);
            }
            storageFileIn = storage.getInputStream(storageFile.getId());
            storageFileOut = storage.getOutputStream(storageFile);
        }
        else
        {
            if (tempData == null)
            {
                tempData = new ByteArrayOutputStream();
            }
        }
        isOpen.set(true);
    }


    @Override
    public ByteBuffer map(int position, long size)
        throws IOException
    {
        throw new UnsupportedOperationException("Cannot map file.");
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
        // we do not need to fill anything
    }


    @Override
    public void delete()
        throws IOException, InterruptedException, ActiveMQException
    {
        if (storage == null)
        {
            filesToBeRemoved.add(fileId);
        }
        else
        {
            storage.removeFile(storageFile.getId());
        }
        factory.deleteFileName(fileId);
        try
        {
            if (isOpen())
            {
                close();
            }
        }
        catch (Exception e)
        {
            LOG.error("Error deleting file.", e);
        }
    }


    @Override
    public void write(ActiveMQBuffer bytes, boolean sync, IOCallback callback)
        throws Exception
    {
        if (timedBuffer != null)
        {
            bytes.setIndex(0, bytes.capacity());
            timedBuffer.addBytes(bytes, sync, callback);
        }
        else
        {
            int readableBytes = bytes.readableBytes();
            ByteBuffer buffer = factory.newBuffer(readableBytes);
            bytes.getBytes(bytes.readerIndex(), buffer);
            buffer.flip();
            writeDirect(buffer, sync, callback);
        }
    }


    @Override
    public void write(ActiveMQBuffer bytes, boolean sync)
        throws Exception
    {
        if (sync)
        {
            SimpleWaitIOCallback completion = new SimpleWaitIOCallback();
            write(bytes, sync, completion);
            completion.waitCompletion();
        }
        else
        {
            write(bytes, sync, DummyCallback.getInstance());
        }
    }


    @Override
    public void write(EncodingSupport bytes, boolean sync, IOCallback callback)
        throws Exception
    {
        if (timedBuffer != null)
        {
            timedBuffer.addBytes(bytes, sync, callback);
        }
        else
        {
            int encodedSize = bytes.getEncodeSize();
            ByteBuffer buffer = factory.newBuffer(encodedSize);
            ActiveMQBuffer outBuffer = ActiveMQBuffers.wrappedBuffer(buffer);
            bytes.encode(outBuffer);
            buffer.clear();
            buffer.limit(encodedSize);
            writeDirect(buffer, sync, callback);
        }
    }


    @Override
    public void write(EncodingSupport bytes, boolean sync)
        throws Exception
    {
        if (sync)
        {
            SimpleWaitIOCallback completion = new SimpleWaitIOCallback();
            write(bytes, sync, completion);
            completion.waitCompletion();
        }
        else
        {
            write(bytes, sync, DummyCallback.getInstance());
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
        writeDirect(bytes, sync, DummyCallback.getInstance());
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
        int bytesRead = 0;
        if (bytes.hasArray())
        {
            bytesRead = storageFileIn.read(bytes.array(), bytes.position() + bytes.arrayOffset(), bytes.remaining());
            readPosition.addAndGet(bytesRead);
            bytes.position(bytes.position() + bytesRead);
            bytes.flip();
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
        readPosition.set(pos);
    }


    @Override
    public long position()
    {
        return readPosition.get();
    }


    @Override
    public void close()
        throws Exception
    {
        if (tempData == null)
        {
            LOG.info("Closed file {}", fileId);
            writeCurrentSizeToFileSizeFile();
            storageFileOut.flush();
            storageFileIn.close();
            storageFileOut.close();
            storageFile = null;
            storageFileIn = null;
            storageFileOut = null;
        }
        position(0);
        isOpen.set(false);
    }


    @Override
    public void sync()
        throws IOException
    {
        measure("sync", () ->
        {
            if ( ++syncCallCounter % 100 == 0)
            {
                LOG.info("Called sync for the {}th time", syncCallCounter);
                LOG.info("Bytes received so far: {}", bytesReceived);
            }
            storageFileOut.flush();
//            storageFileOut.close();
//            storageFile = storage.getFile(fileId);
//            storageFileOut = storage.getOutputStream(storageFile);
            return null;
        });
    }


    @Override
    public long size()
        throws Exception
    {
        return storageFile != null ? storageFile.getSize() + bytesReceived : readFileSizeFromFile();
    }


    @Override
    public void renameTo(String newFileName)
        throws Exception
    {
        factory.changeFileName(newFileName, fileId);
        if (tempData == null)
        {
            InputStream in = storage.getInputStream(storageFile.getId());
            byte[] content = in.readAllBytes();
            in.close();
            StorageFile newFile = storage.createFile(newFileName);
            OutputStream out = storage.getOutputStream(newFile);
            out.write(content);
            out.close();
            storage.removeFile(storageFile.getId());
            storageFile = newFile;
        }
        else
        {
            fileName = newFileName;
        }
    }


    @Override
    public SequentialFile cloneFile()
    {
        return new DataStoreSequentialFile(fileName, storageFile.getId(), factory);
    }


    @Override
    public void copyTo(SequentialFile newFileName)
        throws Exception
    {
        InputStream in = storage.getInputStream(storageFile.getId());
        byte[] content = in.readAllBytes();
        in.close();
        StorageFile newFile = storage.getFile(newFileName.getFileName());
        OutputStream out = storage.getOutputStream(newFile);
        out.write(content);
        out.close();
    }


    @Override
    public void setTimedBuffer(TimedBuffer buffer)
    {
        timedBuffer = buffer;
    }


    @Override
    public File getJavaFile()
    {
        return null;
    }

    private Runnable checkForStorageService = () ->
    {
        while (storage == null)
        {
            try
            {
                storage = StorageService.getStorage();
                LOG.info("Success! Storage is now active. Will write any temporary data to data store.");
                isAlreadyLookingForStorage = false;
                if (tempData != null)
                {
                    for (String s : filesToBeRemoved)
                    {
                        storage.removeFile(s);
                    }
                    synchronized (tempDataSemaphore)
                    {
                        open();
                        byte[] data = tempData.toByteArray();
                        tempData.close();
                        tempData = null;
                        internalWrite(ByteBuffer.wrap(data), true, null, true);
                    }
                }
            }
            catch (StorageException e)
            {
                LOG.warn("Failed to get storage. Will try again. Error msg: {}", e.getMessage());
                try
                {
                    Thread.sleep(3000);
                }
                catch (InterruptedException e1)
                {
                    Thread.currentThread().interrupt();
                }
            }
            catch (Exception e)
            {
                LOG.error("Failed to open file.", e);
            }
        }
    };

    private void getStorage()
    {
        if (storage != null || isAlreadyLookingForStorage)
        {
            return;
        }
        try
        {
            storage = StorageService.getStorage();
        }
        catch (StorageException e)
        {
            LOG.error("Failed to get Storage initially. Will try in the background.");
            isAlreadyLookingForStorage = true;
            new Thread(checkForStorageService).start();
        }
    }


    private void internalWrite(ByteBuffer bytes, boolean sync, IOCallback callback, boolean releaseBuffer)
    {
        try
        {
            if (bytes.hasArray())
            {
                if (tempData == null)
                {
                    bytesReceived += bytes.remaining();
                    storageFileOut.write(bytes.array(), bytes.arrayOffset() + bytes.position(), bytes.remaining());
                    bytes.position(bytes.limit());
                }
                else
                {
                    synchronized (tempDataSemaphore)
                    {
                        tempData.write(bytes.array(), bytes.arrayOffset() + bytes.position(), bytes.remaining());
                    }
                }
            }

            if (/*sync &&*/ tempData == null && bytesReceived % 9000 == 0 && bytesReceived > 0) // will never flush the remaining bytes
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
            LOG.error("Error writing to output stream.", e);
        }
        finally
        {
            if (releaseBuffer)
            {
                factory.releaseBuffer(bytes);
            }
        }
    }


    private <T> T measure(String message, Callable<T> action)
        throws RuntimeException
    {
        long startTime = System.currentTimeMillis();
        try
        {
            T result = action.call();
            return result;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            long delta = System.currentTimeMillis() - startTime;
            if (delta > 20)
            {
                LOG.warn("{} took {}ms", message, delta);
            }
        }
    }


    private void createFileSizeFile()
    {
        fileSizeFile = Paths.get(System.getProperty("bisas.temp"), "file-size-file.txt");
        if (Files.notExists(fileSizeFile))
        {
            try
            {
                Files.createFile(fileSizeFile);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }


    private void writeCurrentSizeToFileSizeFile()
    {
        updateValue(storageFile.getSize() + bytesReceived);
    }


    private void writeSizeToFileSizeFile(long size)
    {
        updateValue(size);
    }


    private void updateValue(long size)
    {
        Map<String, Long> fileSizes = parseFileSizeFile();
        fileSizes.put(fileId, size);
        try
        {
            Files.delete(fileSizeFile);
            createFileSizeFile();
            fileSizes.entrySet().stream().forEach(entry ->
            {
                try
                {
                    Files.writeString(fileSizeFile, String.format("%s:%d%n", entry.getKey(), entry.getValue()), StandardOpenOption.APPEND);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            });
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }


    private long readFileSizeFromFile()
    {
        try
        {
            return parseFileSizeFile().get(fileId);
        }
        catch (NumberFormatException e)
        {
            e.printStackTrace();
        }
        return -1;
    }


    private Map<String, Long> parseFileSizeFile()
    {
        Map<String, Long> fileSizes = new HashMap<>();
        try
        {
            List<String> lines = Files.readAllLines(fileSizeFile);
            lines.stream().forEach(line ->
            {
                String id = line.split(":")[0];
                long size = Long.parseLong(line.split(":")[1]);
                fileSizes.put(id, size);
            });
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return fileSizes;
    }
}
