package ru.spbau.lupuleac.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class NonBlockingBufferWrapper {
    protected ByteBuffer data;
    protected ByteBuffer size;
    protected SocketChannel channel;
    protected volatile int state;

    public static final int READ_SIZE = 1;
    public static final int READ_DATA = 2;
    public static final int PROCESS_DATA = 3;
    public static final int WRITE = 4;

    public NonBlockingBufferWrapper(SocketChannel channel){
        this.channel = channel;
        state = READ_SIZE;
        size = ByteBuffer.allocate(4);
    }

    public int readSize() throws IOException {
        int read = 0;
        try {
            read = channel.read(size);
        }
        catch (IOException e){
            read = -1;
        }
        if(read == -1){
            channel.close();
            state = 0;
        }
        if(size.remaining() == 0){
            size.flip();
            data = ByteBuffer.allocate(size.getInt());
            size.clear();
            state = READ_DATA;
        }
        return state;
    }

    public int readData() throws IOException {
        int read = 0;
        try {
            read = channel.read(data);
        }
        catch (IOException e){
            read = -1;
        }
        if(read == -1){
            System.err.println("Oups");
            channel.close();
            state = 0;
        }
        if(data.remaining() == 0){
            data.flip();
            state = PROCESS_DATA;
        }
        return state;
    }

    public int processData(){
        data.flip();
        state = WRITE;
        return state;
    }

    public int write() throws IOException {
        channel.write(data);
        if (!data.hasRemaining()) {
            data.clear();
            state = READ_SIZE;
        }
        return state;
    }


    public int getState(){
        return state;
    }
}
