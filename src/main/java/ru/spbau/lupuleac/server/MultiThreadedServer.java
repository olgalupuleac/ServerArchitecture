package ru.spbau.lupuleac.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static ru.spbau.lupuleac.server.Utils.*;

public class MultiThreadedServer extends Server {
    private static final Logger LOGGER = Logger.getLogger("MultiThreadedServer");
    private ServerSocket serverSocket;
    private List<Thread> threads = new ArrayList<>();


    public MultiThreadedServer(int port, int numberOfClients, int queriesPerClient) throws IOException {
        super(port, numberOfClients, queriesPerClient);
        serverSocket = new ServerSocket(portNumber);
    }

    @Override
    public void start() throws IOException {
        for (int i = 0; i < numberOfClients; i++) {
            Socket clientSocket = serverSocket.accept();
            LOGGER.info("Connection created");
            processClient(clientSocket);
            if(exception != null){
                throw exception;
            }
        }
        for(Thread t : threads){
            try {
                t.join();
            } catch (InterruptedException ignored) {
            }
        }
        LOGGER.info("Ok");
    }

    private void processClient(Socket client) {
        Thread thread = new Thread(() -> {
            try {
                DataOutputStream out = new DataOutputStream(client.getOutputStream());
                DataInputStream in = new DataInputStream(client.getInputStream());
                for (int i = 0; i < queriesPerClient; i++) {
                    int size = in.readInt();
                    long startProcessing = System.currentTimeMillis();
                    int[] arrayToSort = getArray(in);
                    long startSort = System.currentTimeMillis();
                    sort(arrayToSort);
                    long endSort = System.currentTimeMillis();
                    timeForSort.addAndGet(endSort - startSort);
                    sendArray(arrayToSort, out);
                    long endProcessing = System.currentTimeMillis();
                    timeToProcessQueries.addAndGet(endProcessing - startProcessing);
                }
            } catch (IOException e) {
                handle(e);
            }
        });
        threads.add(thread);
        thread.start();
    }

}