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
        long start = System.currentTimeMillis();
        try(Socket socket = new Socket(host, portNumber)){
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            for (int i = 0; i < numberOfQueries; i++) {
                int[] a = generateArray(elementsInArray);
                sendArray(a, out);
                int size = in.readInt();
                int[] res = getArray(in);
                assert isSorted(res);
                Thread.sleep(timeInterval * 1000);
            }
            System.err.println("Ok");
        } catch (InterruptedException ignored) {
        }
        return (double) (System.currentTimeMillis() - start) / numberOfQueries;
    }
}
