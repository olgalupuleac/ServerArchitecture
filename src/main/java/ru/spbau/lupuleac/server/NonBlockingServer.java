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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static ru.spbau.lupuleac.server.Utils.sort;

public class NonBlockingServer extends Server {
    private static final int HEADER_SIZE = 4;
    private static final Logger LOGGER = Logger.getLogger("NonBlockingServer");

    private final ConcurrentLinkedQueue<Client> registerToRead = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Client> registerToWrite = new ConcurrentLinkedQueue<>();
    private CountDownLatch queriesProcessed;
    private AtomicInteger queriesReceived = new AtomicInteger(0);
    private ServerSocketChannel socketChannel;


    private Thread readingThread = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                while (!Thread.interrupted()) {
                    if(readingSelector == null){
                        continue;
                    }
                    //LOGGER.info("In read " + queriesReceived.get());
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
                LOGGER.info("Queries received " + queriesReceived.get() + ", reading thread is over");
            } catch (IOException e) {
               handle(e);
            }
        }
    });

    private Thread writingThread = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                while (!Thread.interrupted()) {
                    if(writingSelector == null){
                        continue;
                    }
                    while (!registerToWrite.isEmpty()) {
                        Client client = registerToWrite.remove();
                        client.channel.register(writingSelector, SelectionKey.OP_WRITE, client);
                    }
                    int ready = writingSelector.select();
                    //LOGGER.info("ready " + ready);
                    if (ready == 0) {
                        continue;
                    }
                    Set<SelectionKey> selectedKeys = writingSelector.selectedKeys();
                    Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();
                        if (key.isWritable()) {
                            //LOGGER.info("Write to channel");
                            writeToChannel((Client) key.attachment());
                        }
                        keyIterator.remove();
                    }

                }
                LOGGER.info("Queries " + queriesProcessed.getCount() + ", writing thread is over");
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
        if (client.action.get() != -1) {
            //LOGGER.info("In write");
            return;
        }
        ByteBuffer buffer = client.bufferForData;
        if (buffer.hasRemaining()) {
            int bytesWritten = channel.write(buffer);
            client.bytesProcessed += bytesWritten;
            //LOGGER.info("Bytes written " + bytesWritten);
        } else {
            queriesProcessed.countDown();
            LOGGER.info("Queries " + queriesProcessed.getCount());
            timeToProcessQueries.addAndGet(System.currentTimeMillis() - client.start);
            client.prepareToWriteToBuffer();
        }
    }

    private void readFromChannel(Client client) throws IOException {
        SocketChannel channel = client.channel;
        if (client.action.get() != 1) {
            //LOGGER.info("In read");
            return;
        }
        ByteBuffer buffer = client.bufferForData;
        if (client.bytesOfData != -1) {
            int bytesRead = channel.read(buffer);
            client.bytesProcessed += bytesRead;
            //LOGGER.info("Bytes read " + bytesRead);
            if (client.isFull()) {
                queriesReceived.incrementAndGet();
                LOGGER.info("queries received " + queriesReceived);
                client.action.set(0);
                //LOGGER.info("Is full");
                threadPool.submit(() -> {
                    LOGGER.info("Submit " + client.id + " iteration " + client.iteration);
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
        readingThread.start();
        writingThread.start();
    }

    @Override
    public void start() throws IOException {
        LOGGER.info("Start");
        queriesProcessed = new CountDownLatch(totalNumOfQueries);
        ThreadFactory namedThreadFactory =
                new ThreadFactoryBuilder().setNameFormat("my-sad-thread-%d").build();
        threadPool = Executors.newFixedThreadPool(4, namedThreadFactory);
        readingThread.setName("reading thread");
        writingThread.setName("writing thread");
        readingSelector = Selector.open();
        writingSelector = Selector.open();
        for (int i = 0; i < numberOfClients; i++) {
            if(exception != null){
                throw exception;
            }
            SocketChannel channel = null;
            while (channel == null) {
                channel = socketChannel.accept();
            }
            LOGGER.info("Client connected");
            Client client = new Client(channel, i);
            channel.configureBlocking(false);
            registerToRead.add(client);
            readingSelector.wakeup();
            registerToWrite.add(client);
            writingSelector.wakeup();
        }
        try {
            queriesProcessed.await();
        } catch (InterruptedException ignored) {
        }
        LOGGER.info("OK");
    }

    public void shutDown(){
        threadPool.shutdown();
        readingThread.interrupt();
        writingThread.interrupt();
    }

    private static class Client {
        //1 - readFromChannel, 0 - process, -1 - writeToChannel
        private AtomicInteger action = new AtomicInteger(1);
        private volatile int bytesOfData;
        private volatile int bytesProcessed;
        private final SocketChannel channel;
        private ByteBuffer bufferForSize;
        private ByteBuffer bufferForData;
        private long start;
        private int iteration;
        private int id;

        private boolean isFull() {
            return bytesOfData == bytesProcessed;
        }

        private void prepareToWriteToBuffer() {
            iteration++;
            LOGGER.info("Reading state " + iteration + " " + id);
            start = System.currentTimeMillis();
            bytesOfData = -1;
            bytesProcessed = 0;
            bufferForSize.clear();
            bufferForData.clear();
            action.set(1);
        }

        private void setSize() {
            bufferForSize.flip();
            bytesOfData = bufferForSize.getInt();
            LOGGER.info("Size " + bytesOfData);
            bufferForData = ByteBuffer.allocate(bytesOfData);

            bufferForSize.clear();
        }

        private void prepareToReadFromBuffer(byte[] data) {
            LOGGER.info("Writing state " + iteration);
            bytesProcessed = 0;
            bytesOfData = data.length;
            //bufferForData.clear();
            //LOGGER.info("Buffer size " + bufferForData.capacity() + " data size " + bytesOfData);
            bufferForData = ByteBuffer.allocate(data.length + HEADER_SIZE);
            bufferForData.putInt(1);
            bufferForData.put(data);
            bufferForData.flip();
            action.set(-1);
        }

        private Client(SocketChannel channel, int i) {
            id = i;
            this.channel = channel;
            bytesOfData = -1;
            bufferForSize = ByteBuffer.allocate(HEADER_SIZE);
            action.set(1);
        }
    }
}
