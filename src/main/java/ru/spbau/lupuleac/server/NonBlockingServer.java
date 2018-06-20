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
                            Client client = ((Client) key.attachment());
                            if(client.getState() == NonBlockingBufferWrapper.READ_SIZE){
                                client.readSize();
                            }
                            if(client.getState() == NonBlockingBufferWrapper.READ_DATA){
                                int nextOp = client.readData();
                                if(nextOp == NonBlockingBufferWrapper.PROCESS_DATA){
                                    client.process();
                                }
                            }
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
                            Client client = ((Client) key.attachment());
                            if(client.getState() == NonBlockingBufferWrapper.WRITE){
                                int nextOp = client.write();
                                if(nextOp == NonBlockingBufferWrapper.READ_SIZE){
                                    queriesProcessed.countDown();
                                }
                            }
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

    public NonBlockingServer(int port) throws IOException {
        super(port);
        socketChannel = ServerSocketChannel.open();
        socketChannel.bind(new InetSocketAddress(portNumber));
        readingThread.start();
        writingThread.start();
    }

    @Override
    public void start(int numberOfClients, int queriesPerClient) throws IOException {
        super.start(numberOfClients, queriesPerClient);
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
            Client client = new Client(channel);
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

    private class Client extends NonBlockingBufferWrapper {
        public void process() {
            LOGGER.info("process");
            threadPool.submit(() -> {
                int[] arrayToSort = Utils.getArrayFromBytes(data.array());
                if (arrayToSort == null) {
                    LOGGER.warning("Parse exception");
                    return;
                }
                long start = System.currentTimeMillis();
                sort(arrayToSort);
                timeForSort.addAndGet(System.currentTimeMillis() - start);
                byte[] res = Utils.toByteArray(arrayToSort);
                data = ByteBuffer.allocate(4 + res.length);
                data.putInt(res.length);
                data.put(res);
                processData();
            });
        }

        private Client(SocketChannel channel) {
            super(channel);
        }
    }
}
