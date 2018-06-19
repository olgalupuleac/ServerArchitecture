package ru.spbau.lupuleac.server;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static ru.spbau.lupuleac.server.Utils.sort;

public class NonBlockingServer extends Server {
    private static final int HEADER_SIZE = 4;
    private static final Logger LOGGER = Logger.getLogger("NonBlockingServer");

    private final ConcurrentLinkedQueue<Client> registerToRead = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Client> registerToWrite = new ConcurrentLinkedQueue<>();
    private AtomicInteger queriesProcessed = new AtomicInteger(0);
    private ServerSocketChannel socketChannel;


    private Thread readingThread = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                while (queriesProcessed.get() < totalNumOfQueries) {
                    while (!registerToRead.isEmpty()) {
                        Client client = registerToRead.remove();
                        client.channel.register(readingSelector, SelectionKey.OP_READ, client);
                    }
                    int ready = readingSelector.select();
                    if (ready == 0) {
                        continue;
                    }
                    Set<SelectionKey> selectedKeys = readingSelector.selectedKeys();
                    Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();
                        if (key.isReadable()) {
                            readFromChannel((Client) key.attachment());
                        }
                        keyIterator.remove();
                    }
                }

            } catch (IOException e) {
               handle(e);
            }
        }
    });

    private Thread writingThread = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                while (queriesProcessed.get() < totalNumOfQueries) {
                    while (!registerToWrite.isEmpty()) {
                        Client client = registerToWrite.remove();
                        client.channel.register(writingSelector, SelectionKey.OP_WRITE, client);
                    }
                    int ready = writingSelector.select();
                    if (ready == 0) {
                        continue;
                    }
                    Set<SelectionKey> selectedKeys = writingSelector.selectedKeys();
                    Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();
                        if (key.isWritable()) {
                            writeToChannel((Client) key.attachment());
                        }
                        keyIterator.remove();
                    }
                }
                threadPool.shutdownNow();
                LOGGER.info("Queries processed " + queriesProcessed.get() + ", writing thread is over");
            } catch (IOException e) {
                handle(e);
            }
        }
    });


    private Selector readingSelector;
    private Selector writingSelector;
    private ExecutorService threadPool;


    private void writeToChannel(Client client) throws IOException {
        SocketChannel channel = client.channel;
        if (client.toBeReadIn.get()) {
            return;
        }
        ByteBuffer buffer = client.bufferForData;
        if (buffer.hasRemaining()) {
            int bytesWritten = channel.write(buffer);
            client.bytesProcessed += bytesWritten;
        } else {
            queriesProcessed.incrementAndGet();
            timeToProcessQueries.addAndGet(System.currentTimeMillis() - client.start);
            client.prepareToWriteToBuffer();
        }
    }

    private void readFromChannel(Client client) throws IOException {
        SocketChannel channel = client.channel;
        if (!client.toBeReadIn.get()) {
            return;
        }
        ByteBuffer buffer = client.bufferForData;
        if (client.bytesOfData != -1) {
            int bytesRead = channel.read(buffer);
            client.bytesProcessed += bytesRead;
            LOGGER.info("Bytes processed " + client.bytesProcessed + ", bytes of data " + client.bytesOfData);
            if (client.isFull()) {
                threadPool.submit(() -> {
                    int[] arrayToSort = Utils.getArrayFromBytes(buffer.array());
                    if (arrayToSort == null) {
                        LOGGER.warning("Parse exception");
                        return;
                    }
                    long start = System.currentTimeMillis();
                    sort(arrayToSort);
                    timeForSort.addAndGet(System.currentTimeMillis() - start);
                    byte[] res = Utils.toByteArray(arrayToSort);
                    client.prepareToReadFromBuffer(res);
                });
            }
        } else {
            channel.read(client.bufferForSize);
            if (client.bufferForSize.position() == client.bufferForSize.limit()) {
                client.setSize();
            }
        }
    }


    public NonBlockingServer(int port, int numberOfClients, int queriesPerClient) throws IOException {
        super(port, numberOfClients, queriesPerClient);
        socketChannel = ServerSocketChannel.open();
        socketChannel.bind(new InetSocketAddress(portNumber));
    }

    @Override
    public void start() throws IOException {

        ThreadFactory namedThreadFactory =
                new ThreadFactoryBuilder().setNameFormat("my-sad-thread-%d").build();
        threadPool = Executors.newFixedThreadPool(4, namedThreadFactory);
        readingThread.setName("reading thread");
        writingThread.setName("writing thread");
        readingSelector = Selector.open();
        writingSelector = Selector.open();
        readingThread.start();
        writingThread.start();
        for (int i = 0; i < numberOfClients; i++) {
            if(exception != null){
                throw exception;
            }
            SocketChannel channel = null;
            while (channel == null) {
                channel = socketChannel.accept();
            }
            LOGGER.info("Client connected");
            Client client = new Client(channel);
            channel.configureBlocking(false);
            registerToRead.add(client);
            readingSelector.wakeup();
            registerToWrite.add(client);
            writingSelector.wakeup();
        }
        LOGGER.info("OK");
        try {
            readingThread.join();
            writingThread.join();
        } catch (InterruptedException ignored){}
    }

    private static class Client {
        private AtomicBoolean toBeReadIn = new AtomicBoolean();
        private volatile int bytesOfData;
        private volatile int bytesProcessed;
        private final SocketChannel channel;
        private ByteBuffer bufferForSize;
        private ByteBuffer bufferForData;
        private long start;

        private boolean isFull() {
            return bytesOfData == bytesProcessed;
        }

        private void prepareToWriteToBuffer() {
            start = System.currentTimeMillis();
            bytesOfData = -1;
            bytesProcessed = 0;
            bufferForSize.clear();
            bufferForData.clear();
            toBeReadIn.set(true);
        }

        private void setSize() {
            bufferForSize.flip();
            bytesOfData = bufferForSize.getInt();
            LOGGER.info("Size " + bytesOfData);
            bufferForData = ByteBuffer.allocate(bytesOfData);
            bufferForSize.clear();
        }

        private void prepareToReadFromBuffer(byte[] data) {
            bytesProcessed = 0;
            bufferForData.clear();
            //bufferForData.putInt(data.length);
            bufferForData.put(data);
            bufferForData.flip();
            toBeReadIn.set(false);
        }

        private Client(SocketChannel channel) {
            this.channel = channel;
            bytesOfData = -1;
            toBeReadIn.set(true);
            bufferForSize = ByteBuffer.allocate(HEADER_SIZE);
        }
    }
}
