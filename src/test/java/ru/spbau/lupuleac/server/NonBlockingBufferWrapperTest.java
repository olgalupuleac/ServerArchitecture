package ru.spbau.lupuleac.server;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.channels.Channels;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class NonBlockingBufferWrapperTest {
    @Test
    public void test() throws Exception {
        int[] array = {1, 2, 3};
        ByteArrayOutputStream userInput = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(userInput);
        Utils.sendArray(array, dataOutputStream);
        byte[] inputForServer = userInput.toByteArray();

        //expected output
        byte[] serialized = Utils.toByteArray(array);
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        DataOutputStream formatted = new DataOutputStream(expected);
        formatted.writeInt(serialized.length);
        formatted.write(serialized);

        ByteArrayOutputStream realOutput = new ByteArrayOutputStream();
        ClientHandler clientHandler = new ClientHandler(
                Channels.newChannel(new ByteArrayInputStream(inputForServer)),
                Channels.newChannel(realOutput),
                new CountDownLatch(1),
                new AtomicLong(0)
        );
        int firstState = clientHandler.readSize();
        assertEquals(ClientHandler.READ_DATA, firstState);
        int secondState = clientHandler.readData();
        assertEquals(ClientHandler.PROCESS_DATA, secondState);
        clientHandler.process();
        while(clientHandler.getState() != ClientHandler.WRITE){
            continue;
        }
        int lastState = clientHandler.write();
        assertEquals(ClientHandler.READ_SIZE, lastState);
        assertEquals(0, clientHandler.queriesProcessed.getCount());
        assertArrayEquals(expected.toByteArray(), realOutput.toByteArray());
    }

    @Test
    public void testSeveralQueries() throws Exception {
        int numberOfQueries = 10000;
        ByteArrayOutputStream userInput = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(userInput);
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        DataOutputStream formatted = new DataOutputStream(expected);
        for(int i = 0; i < numberOfQueries; i++){
            int[] array = {i, i + 1, i + 2};
            Utils.sendArray(array, dataOutputStream);
            byte[] serialized = Utils.toByteArray(array);
            formatted.writeInt(serialized.length);
            formatted.write(serialized);
        }

        byte[] inputForServer = userInput.toByteArray();
        ByteArrayOutputStream realOutput = new ByteArrayOutputStream();
        ClientHandler clientHandler = new ClientHandler(
                Channels.newChannel(new ByteArrayInputStream(inputForServer)),
                Channels.newChannel(realOutput),
                new CountDownLatch(numberOfQueries),
                new AtomicLong(0)
        );
        for(int i = 0; i < numberOfQueries; i++){
            int firstState = clientHandler.readSize();
            assertEquals(ClientHandler.READ_DATA, firstState);
            int secondState = clientHandler.readData();
            assertEquals(ClientHandler.PROCESS_DATA, secondState);
            clientHandler.process();
            while(clientHandler.getState() != ClientHandler.WRITE){
                continue;
            }
            int lastState = clientHandler.write();
            assertEquals(ClientHandler.READ_SIZE, lastState);
            assertEquals(numberOfQueries - i - 1, clientHandler.queriesProcessed.getCount());
        }
        assertArrayEquals(expected.toByteArray(), realOutput.toByteArray());
    }

}