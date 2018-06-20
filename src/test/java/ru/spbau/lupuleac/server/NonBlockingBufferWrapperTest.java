package ru.spbau.lupuleac.server;

import java.nio.channels.SocketChannel;

import static org.junit.Assert.*;

public class NonBlockingBufferWrapperTest {
    private static class Client extends NonBlockingBufferWrapper {
        public Client(SocketChannel channel) {
            super(channel);
        }

        @Override
        public void process() {

        }
    }

}