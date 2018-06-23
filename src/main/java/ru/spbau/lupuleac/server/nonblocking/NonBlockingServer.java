package ru.spbau.lupuleac.server.nonblocking;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import ru.spbau.lupuleac.server.Server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class NonBlockingServer extends Server {
    private static final Logger LOGGER = Logger.getLogger("NonBlockingServer");

    private final List<ClientHandler> clients = new ArrayList<>();
    private final ConcurrentLinkedQueue<ClientHandler> registerToRead = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ClientHandler> registerToWrite = new ConcurrentLinkedQueue<>();
    private ServerSocketChannel socketChannel;
    private CountDownLatch queriesProcessed = new CountDownLatch(totalNumOfQueries);


    private Thread readingThread = new Thread(new Runnable() {
        @Override
        public void run() {
            while (!Thread.interrupted()) {
                if (readingSelector == null) {
                    continue;
                }
                while (!registerToRead.isEmpty()) {
                    ClientHandler client = registerToRead.remove();
                    try {
                        ((SocketChannel) client.getReadableChannel()).
                                register(readingSelector, SelectionKey.OP_READ, client);
                    } catch (ClosedChannelException e) {
                        handle(e, client.getReadableChannel());
                    }
                }
                int ready = 0;
                try {
                    ready = readingSelector.select();
                } catch (IOException e) {
                    exception = e;
                }
                if (ready == 0) {
                    continue;
                }
                Set<SelectionKey> selectedKeys = readingSelector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    if (key.isReadable()) {
                        ClientHandler client = ((ClientHandler) key.attachment());
                        if (client.getState() == ClientHandler.READ_SIZE) {
                            try {
                                client.readSize();
                            } catch (IOException e) {
                                handle(e, client.getReadableChannel());
                            }
                        }
                        if (client.getState() == ClientHandler.READ_DATA) {
                            int nextOp = 0;
                            try {
                                nextOp = client.readData();
                            } catch (IOException e) {
                                handle(e, client.getReadableChannel());
                            }
                            if (nextOp == ClientHandler.PROCESS_DATA) {
                                threadPool.submit((Runnable) client::process);
                            }
                        }
                    }
                    keyIterator.remove();
                }
            }
        }
    });

    private Thread writingThread = new Thread(new Runnable() {
        @Override
        public void run() {
            while (!Thread.interrupted()) {
                if (writingSelector == null) {
                    continue;
                }
                while (!registerToWrite.isEmpty()) {
                    ClientHandler client = registerToWrite.remove();
                    try {
                        ((SocketChannel) client.getWritableChannel()).
                                register(writingSelector, SelectionKey.OP_WRITE, client);
                    } catch (ClosedChannelException e) {
                        handle(e, client.getWritableChannel());
                    }
                }
                int ready = 0;
                try {
                    ready = writingSelector.select();
                } catch (IOException e) {
                    exception = e;
                }
                if (ready == 0) {
                    continue;
                }
                Set<SelectionKey> selectedKeys = writingSelector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    try {
                        if (key.isWritable()) {
                            ClientHandler client = ((ClientHandler) key.attachment());
                            if (client.getState() == ClientHandler.WRITE) {
                                try {
                                    client.write();
                                } catch (IOException e) {
                                    handle(e, client.getWritableChannel());
                                }
                            }
                        }
                        keyIterator.remove();
                    } catch (CancelledKeyException ignored) {
                    }
                }

            }
        }

    });


    private Selector readingSelector;
    private Selector writingSelector;
    private ExecutorService threadPool;

    public NonBlockingServer(int port,
                             int numberOfClients,
                             int queriesPerClient) throws IOException {
        super(port, numberOfClients, queriesPerClient);
        socketChannel = ServerSocketChannel.open();
        socketChannel.bind(new InetSocketAddress(portNumber));
        readingThread.start();
        writingThread.start();
    }

    @Override
    public void start() throws IOException {
        LOGGER.info("Start");
        ThreadFactory namedThreadFactory =
                new ThreadFactoryBuilder().setNameFormat("my-sad-thread-%d").build();
        threadPool = Executors.newFixedThreadPool(4, namedThreadFactory);
        readingThread.setName("reading thread");
        writingThread.setName("writing thread");
        readingSelector = Selector.open();
        writingSelector = Selector.open();
        for (int i = 0; i < numberOfClients; i++) {
            if (exception != null) {
                throw exception;
            }
            SocketChannel channel = null;
            while (channel == null) {
                channel = socketChannel.accept();
            }
            ClientHandler client = new ClientHandler(channel,
                    channel, queriesProcessed, timeForSort,
                    queriesPerClient);
            channel.configureBlocking(false);
            clients.add(client);
            registerToRead.add(client);
            readingSelector.wakeup();
            registerToWrite.add(client);
            writingSelector.wakeup();
        }
        try {
            queriesProcessed.await();
            if(exception != null){
                shutDown();
                throw exception;
            }
            clients.forEach(x -> {
                timeToProcessQueries.addAndGet(x.getTime());
                System.err.println(x.getTime());
            });
            System.err.println("Process " + timeToProcessQueries.get());
            System.err.println("Sort " + timeForSort.get());
            System.err.println("Average process " + getAverageTimeForProcessingQuery());
            System.err.println("Average sort " + getAverageSortTime());
            LOGGER.info("OK");
        } catch (InterruptedException ignored) {
        }
    }

    public void shutDown() throws IOException {
        threadPool.shutdown();
        readingThread.interrupt();
        writingThread.interrupt();
        socketChannel.close();
    }

    private void handle(IOException e, Channel channel) {
        exception = e;
        try {
            channel.close();
        } catch (IOException e1) {
            exception.addSuppressed(e1);
        }
        while(queriesProcessed.getCount() != 0){
            queriesProcessed.countDown();
        }
    }
}
