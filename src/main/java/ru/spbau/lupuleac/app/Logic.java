package ru.spbau.lupuleac.app;

import ru.spbau.lupuleac.server.BlockingServer;
import ru.spbau.lupuleac.server.MultiThreadedServer;
import ru.spbau.lupuleac.server.NonBlockingServer;
import ru.spbau.lupuleac.server.Server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class Logic {
    private static final Logger LOGGER = Logger.getLogger("Logic");
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

    public Map<Integer, TestResult> startTesting(String host, MyApplication.Design design,
                                                 MyApplication.ChangingParameter parameter, String serverHost,
                                                 int... args) throws IOException {
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
        Socket socket = new Socket(host, clientPort);
        startServer();
        DataInputStream in = new DataInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        HashMap<Integer, TestResult> testResults = new HashMap<>();
        do {
            sendRequestToClientManager(out);
            server.start(numberOfClients, queriesPerClient);
            double clientTime = in.readDouble();
            double sortTime = server.getAverageSortTime();
            double queryTime = server.getAverageTimeForProcessingQuery();
            testResults.put(getChangingParameter(), new TestResult(sortTime, queryTime, clientTime));
        } while (isTested());
        if(server instanceof NonBlockingServer){
            ((NonBlockingServer)server).shutDown();
        }
        LOGGER.info("All tests");
        return testResults;
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

    private int getChangingParameter(){
        switch (parameter){
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

    private void startServer() throws IOException {
        switch (design){
            case BLOCKING:
                server = new BlockingServer(portForServer);
                break;
            case NONBLOCKING:
                server = new NonBlockingServer(portForServer);
                break;
            case MULTITHREADED:
                server = new MultiThreadedServer(portForServer);
                break;
        }
    }

    public static class TestResult {
        private double sortedTime;
        private double queryTime;
        private double clientTime;

        public TestResult(double sortedTime, double queryTime, double clientTime){
            this.clientTime = clientTime;
            this.sortedTime = sortedTime;
            this.queryTime = queryTime;
        }

        public double getSortedTime() {
            return sortedTime;
        }

        public void setSortedTime(double sortedTime) {
            this.sortedTime = sortedTime;
        }

        public double getQueryTime() {
            return queryTime;
        }

        public double getClientTime() {
            return clientTime;
        }
    }
}
