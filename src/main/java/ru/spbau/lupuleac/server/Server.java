package ru.spbau.lupuleac.server;

import java.io.IOException;
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

    public Server(int port){
        this.portNumber = port;
    }

    protected synchronized void handle(IOException e){
        if(exception != null){
            exception.addSuppressed(e);
        }
        else {
            exception = e;
        }
    }

    public double getAverageSortTime(){
        return (double) timeForSort.get() / totalNumOfQueries;
    }

    public double getAverageTimeForProcessingQuery(){
        return (double) timeToProcessQueries.get() / totalNumOfQueries;
    }

    public  void start(int numberOfClients, int queriesPerClient) throws IOException {
        this.numberOfClients = numberOfClients;
        this.queriesPerClient = queriesPerClient;
        timeForSort = new AtomicLong(0);
        timeToProcessQueries = new AtomicLong(0);
        totalNumOfQueries = numberOfClients * queriesPerClient;
    }
}
