package ru.spbau.lupuleac.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import static ru.spbau.lupuleac.server.Utils.*;

public class BlockingServer extends Server {
    private static final Logger LOGGER = Logger.getLogger("BlockingServer");
    private ExecutorService threadPool = Executors.newFixedThreadPool(4);
    private ServerSocket serverSocket;

    public BlockingServer(int port, int numberOfClients, int queriesPerClient) throws IOException {
        super(port, numberOfClients, queriesPerClient);
        serverSocket = new ServerSocket(portNumber);
        LOGGER.info("Server started");
    }

    @Override
    public void start() throws IOException {
        for (int i = 0; i < numberOfClients; i++) {
            if(exception != null){
                throw exception;
            }
            Socket clientSocket = serverSocket.accept();
            LOGGER.info("Client connected");
            processClient(clientSocket);
        }
    }

    private void processClient(Socket client) {
        ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
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
                        } catch (IOException e) {
                            handle(e);
                        }
                    });
                }
            } catch (IOException e) {
                handle(e);
            } catch (InterruptedException | ExecutionException ignored) {
            }
        });
        thread.start();
    }
}
