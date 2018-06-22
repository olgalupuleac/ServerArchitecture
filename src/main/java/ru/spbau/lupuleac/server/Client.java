package ru.spbau.lupuleac.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

import static ru.spbau.lupuleac.server.Utils.*;

public class Client implements Callable {
    private String host;
    private int portNumber;
    private int numberOfQueries;
    private int elementsInArray;
    private int timeInterval;

    public Client(String host, int... args){
        this.host = host;
        portNumber = args[0];
        elementsInArray = args[1];
        numberOfQueries = args[2];
        timeInterval = args[3];
    }

    public Double call() throws IOException {
        System.err.println("hello");
        long start = System.currentTimeMillis();
        try(Socket socket = new Socket(host, portNumber)){
            System.err.println(numberOfQueries);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            for (int i = 0; i < numberOfQueries; i++) {
                int[] a = generateArray(elementsInArray);
                sendArray(a, out);
                in.readInt();
                int[] res = getArray(in);
                //assert isSorted(res);
                Thread.sleep(timeInterval);
            }
            System.err.println("Ok");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return (double) (System.currentTimeMillis() - start) / numberOfQueries;
    }
}
