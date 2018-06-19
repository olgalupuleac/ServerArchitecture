package ru.spbau.lupuleac.app;

import ru.spbau.lupuleac.server.Client;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.logging.Logger;

public class ClientManager {
    private static final Logger LOGGER = Logger.getLogger("ClientManager");
    public static void main(String[] args) {
        int portNumber = Integer.parseInt(args[0]);
        try {
            ServerSocket serverSocket = new ServerSocket(portNumber);
            Socket client = serverSocket.accept();
            for (int it = 0; ; it++){
                ClientInfoProtocol.ClientInfo clientInfo = ClientInfoProtocol.ClientInfo.parseDelimitedFrom(
                        client.getInputStream());
                int port = clientInfo.getPort();
                int queriesPerClient = clientInfo.getQueriesPerClient();
                int numberOfClients = clientInfo.getNumberOfClients();
                int elementsInArray = clientInfo.getElementsInArray();
                int timeInterval = clientInfo.getTimeInterval();
                LOGGER.info("Number of elements in array " + elementsInArray);
                LOGGER.info("received");
                Thread.sleep(1000);
                List<FutureTask<Double>> clients = new ArrayList<>();
                for(int i = 0; i < numberOfClients; i++){
                    Client clientForTest = new Client(clientInfo.getHost(), port,
                            elementsInArray, queriesPerClient, timeInterval);
                    FutureTask<Double> futureTask = new FutureTask<>(clientForTest);
                    clients.add(futureTask);
                    Thread t = new Thread(futureTask);
                    t.start();
                }
                double sum = 0;
                for(FutureTask task : clients){
                    try {
                        sum += (Double) task.get();
                    } catch (ExecutionException ignored) {
                    }
                }
                DataOutputStream out = new DataOutputStream(client.getOutputStream());
                out.writeDouble(sum / numberOfClients);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException ignored) {

        }
    }
}
