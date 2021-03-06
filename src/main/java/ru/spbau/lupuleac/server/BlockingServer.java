package ru.spbau.lupuleac.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.logging.Logger;

import static ru.spbau.lupuleac.server.Utils.*;

public class BlockingServer extends Server {
    private static final Logger LOGGER = Logger.getLogger("BlockingServer");
    private ExecutorService threadPool;
    private ServerSocket serverSocket;
    private CountDownLatch queriesProcessed;
    private ConcurrentLinkedQueue<ExecutorService> singleThreadExecutors;

    public BlockingServer(int port, int numberOfClients, int queriesPerClient) throws IOException {
        super(port, numberOfClients, queriesPerClient);
    }

    @Override
    public void start() throws IOException {
        singleThreadExecutors = new ConcurrentLinkedQueue<>();
        serverSocket = new ServerSocket(portNumber);
        threadPool = Executors.newFixedThreadPool(4);
        LOGGER.info("Start");
        queriesProcessed = new CountDownLatch(totalNumOfQueries);
        for (int i = 0; i < numberOfClients; i++) {
            if(exception != null){
                shutDown();
                throw exception;
            }
            Socket clientSocket = null;
            try {
                clientSocket = serverSocket.accept();
            } catch (IOException e) {
                exception = e;
            }
            processClient(clientSocket);
        }
        try {
            queriesProcessed.await();
        } catch (InterruptedException ignored) {
        }
    }

    @Override
    public void shutDown() throws IOException {
        threadPool.shutdown();
        while (!singleThreadExecutors.isEmpty()){
            ExecutorService executorService = singleThreadExecutors.remove();
            executorService.shutdown();
        }
        serverSocket.close();
    }


    private void processClient(Socket client) {
        ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
        singleThreadExecutors.add(singleThreadExecutor);
        Thread thread = new Thread(() -> {
            try {
                DataOutputStream out = new DataOutputStream(client.getOutputStream());
                DataInputStream in = new DataInputStream(client.getInputStream());
                for (int i = 0; i < queriesPerClient; i++) {
                    int size = in.readInt();
                    long startProcessing = System.currentTimeMillis();
                    int[] arrayToSort = getArray(in);
                    Future<int[]> future = threadPool.submit(() -> {
                        long startSort = System.currentTimeMillis();
                        sort(arrayToSort);
                        timeForSort.addAndGet(System.currentTimeMillis() - startSort);
                        return arrayToSort;
                    });
                    int[] sortedArray = future.get();
                    singleThreadExecutor.submit(() -> {
                        try {
                            sendArray(sortedArray, out);
                            timeToProcessQueries.addAndGet(System.currentTimeMillis() - startProcessing);
                            queriesProcessed.countDown();
                        } catch (IOException e) {
                            handle(e, client);
                        }
                    });
                }
            } catch (IOException e) {
                handle(e, client);
            } catch (InterruptedException | ExecutionException ignored) {
            }
        });
        thread.start();
    }
}
