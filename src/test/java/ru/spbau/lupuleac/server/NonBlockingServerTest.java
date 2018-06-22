package ru.spbau.lupuleac.server;

import org.junit.Test;
import ru.spbau.lupuleac.server.nonblocking.NonBlockingServer;

import java.io.IOException;

public class NonBlockingServerTest {
    @Test
    public void test() throws Exception{
        int numberOfClients = 10;
        int queriesPerClient = 10000;
        NonBlockingServer server = new NonBlockingServer(1550, numberOfClients, queriesPerClient);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    server.start();
                    System.err.println(server.getAverageTimeForProcessingQuery());
                    server.shutDown();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        t.start();
        Thread.sleep(100);
        for(int i = 0; i < numberOfClients; i++){
            Client client = new Client("localhost", 1550, 10, queriesPerClient, 0);
            Thread thread = new Thread(() -> {
                try {
                    client.call();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            thread.start();
        }
    }
}
