package ru.spbau.lupuleac.app;

import javafx.concurrent.Task;
import ru.spbau.lupuleac.server.BlockingServer;
import ru.spbau.lupuleac.server.MultiThreadedServer;
import ru.spbau.lupuleac.server.nonblocking.NonBlockingServer;
import ru.spbau.lupuleac.server.Server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class ServerTask extends Task<Map<Integer, ServerTask.TestResult>> {
    private static final Logger LOGGER = Logger.getLogger("ServerTask");
    private Server server;
    private MyApplication.ChangingParameter parameter;
    private MyApplication.Design design;
    private int clientPort;
    private int portForServer;
    private int queriesPerClient;
    private int numberOfClients;
    private int elementsInArray;
    private int timeInterval;
    private int step;
    private int upperLimit;
    private String serverHost;
    private String hostForClientManager;

    public ServerTask(String host, MyApplication.Design design,
                                                 MyApplication.ChangingParameter parameter, String serverHost,
                                                 int... args){
        hostForClientManager = host;
        this.design = design;
        this.parameter = parameter;
        this.serverHost = serverHost;
        clientPort = args[0];
        portForServer = args[1];
        queriesPerClient = args[2];
        numberOfClients = args[3];
        elementsInArray = args[4];
        timeInterval = args[5];
        step = args[6];
        upperLimit = args[7];
    }

    private void sendRequestToClientManager(DataOutputStream out) throws IOException {
        ClientInfoProtocol.ClientInfo.Builder builder = ClientInfoProtocol.ClientInfo.newBuilder();
        builder.setElementsInArray(elementsInArray);
        builder.setNumberOfClients(numberOfClients);
        builder.setPort(portForServer);
        builder.setTimeInterval(timeInterval);
        builder.setQueriesPerClient(queriesPerClient);
        builder.setHost(serverHost);
        builder.build().writeDelimitedTo(out);
    }

    private int getChangingParameter() {
        switch (parameter) {
            case NUMBER_OF_CLIENTS:
                return numberOfClients;
            case TIME_BETWEEN_QUERIES:
                return timeInterval;
            case NUMBER_OF_ELEMENTS_IN_ARRAY:
                return elementsInArray;
        }
        return 0;
    }

    private boolean isTested() {
        switch (parameter) {
            case NUMBER_OF_CLIENTS:
                return (numberOfClients += step) <= upperLimit;
            case TIME_BETWEEN_QUERIES:
                return (timeInterval += step) <= upperLimit;
            case NUMBER_OF_ELEMENTS_IN_ARRAY:
                return (elementsInArray += step) <= upperLimit;
        }
        return false;
    }

    private Server createServer() throws IOException {
        switch (design) {
            case BLOCKING:
                return new BlockingServer(portForServer, numberOfClients, queriesPerClient);
            case NONBLOCKING:
                return new NonBlockingServer(portForServer, numberOfClients, queriesPerClient);
            case MULTITHREADED:
                return new MultiThreadedServer(portForServer, numberOfClients, queriesPerClient);
        }
        return null;
    }

    @Override
    protected Map<Integer, TestResult> call() throws IOException {
        Socket socket = new Socket(hostForClientManager, clientPort);
        DataInputStream in = new DataInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        HashMap<Integer, TestResult> testResults = new HashMap<>();
        int iteration = 0;
        int total = (upperLimit - getChangingParameter()) / step;
        do {
            server = createServer();
            sendRequestToClientManager(out);
            server.start();
            double clientTime = in.readDouble();
            double sortTime = server.getAverageSortTime();
            double queryTime = server.getAverageTimeForProcessingQuery();
            testResults.put(getChangingParameter(), new TestResult(sortTime, queryTime, clientTime));
            server.shutDown();
           // updateProgress(++iteration, total);
        } while (isTested());
        LOGGER.info("All tests");
        return testResults;
    }

    public static class TestResult {
        private double sortedTime;
        private double queryTime;
        private double clientTime;

        public TestResult(double sortedTime, double queryTime, double clientTime) {
            this.clientTime = clientTime;
            this.sortedTime = sortedTime;
            this.queryTime = queryTime;
        }

        public double getSortedTime() {
            return sortedTime;
        }

        public double getQueryTime() {
            return queryTime;
        }

        public double getClientTime() {
            return clientTime;
        }
    }
}
