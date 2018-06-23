package ru.spbau.lupuleac.server;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public abstract class Server {
    protected int portNumber;
    protected int queriesPerClient;
    protected int numberOfClients;
    protected AtomicLong timeForSort;
    protected AtomicLong timeToProcessQueries;
    protected volatile IOException exception;
    protected int totalNumOfQueries;

    public Server(int port, int numberOfClients, int queriesPerClient){
        this.portNumber = port;
        this.numberOfClients = numberOfClients;
        this.queriesPerClient = queriesPerClient;
        timeForSort = new AtomicLong(0);
        timeToProcessQueries = new AtomicLong(0);
        totalNumOfQueries = numberOfClients * queriesPerClient;
    }

    protected synchronized void handle(IOException e, Socket socket) {
        exception = e;
        try {
            socket.close();
        }
        catch (IOException ex){
            exception.addSuppressed(ex);
        }
    }

    public double getAverageSortTime(){
        System.err.println("time for sort " + timeForSort.get() + ",num of queries " + totalNumOfQueries);
        return (double) timeForSort.get() / totalNumOfQueries;
    }

    public double getAverageTimeForProcessingQuery(){
        System.err.println("time for sort " + timeToProcessQueries.get()
                + ",num of queries " + totalNumOfQueries);
        return (double) timeToProcessQueries.get() / totalNumOfQueries;
    }

    public abstract void start() throws IOException;

    public abstract void shutDown() throws IOException;

    public IOException getException() {
        return exception;
    }
}
