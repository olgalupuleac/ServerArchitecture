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


    private Thread readingThread = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                while (!Thread.interrupted()) {
                    if(readingSelector == null){
                        continue;
                    }
                    while (!registerToRead.isEmpty()) {
                        ClientHandler client = registerToRead.remove();
                        ((SocketChannel) client.getReadableChannel()).
                                register(readingSelector, SelectionKey.OP_READ, client);
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
                            ClientHandler client = ((ClientHandler) key.attachment());
                            if(client.getState() == ClientHandler.READ_SIZE){
                                client.readSize();
                            }
                            if(client.getState() == ClientHandler.READ_DATA){
                                int nextOp = client.readData();
                                if(nextOp == ClientHandler.PROCESS_DATA){
                                    threadPool.submit((Runnable) client::process);
                                }
                            }
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
                while (!Thread.interrupted()) {
                    if(writingSelector == null){
                        continue;
                    }
                    while (!registerToWrite.isEmpty()) {
                        ClientHandler client = registerToWrite.remove();
                        ((SocketChannel)client.getWritableChannel()).
                                register(writingSelector, SelectionKey.OP_WRITE, client);
                    }
                    int ready = writingSelector.select();
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
                                if(client.getState() == ClientHandler.WRITE){
                                    client.write();
                                }
                            }
                            keyIterator.remove();
                        } catch (CancelledKeyException ignored){
                        }
                    }

                }
            } catch (IOException e) {
                handle(e);
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
        CountDownLatch queriesProcessed = new CountDownLatch(totalNumOfQueries);
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

    public void shutDown() throws IOException{
        threadPool.shutdown();
        readingThread.interrupt();
        writingThread.interrupt();
        socketChannel.close();
    }
}
