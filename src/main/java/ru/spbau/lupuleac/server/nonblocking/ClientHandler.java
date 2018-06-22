package ru.spbau.lupuleac.server.nonblocking;

import ru.spbau.lupuleac.server.Utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import static ru.spbau.lupuleac.server.Utils.sort;

public class ClientHandler {
    protected ByteBuffer data;
    protected ByteBuffer size;
    protected WritableByteChannel writableChannel;
    protected ReadableByteChannel readableChannel;
    protected AtomicLong timeForSort;
    protected volatile int state;
    protected CountDownLatch queriesProcessedTotal;
    protected int queriesToDo;
    protected long start;
    protected long time;

    public static final int READ_SIZE = 1;
    public static final int READ_DATA = 2;
    public static final int PROCESS_DATA = 3;
    public static final int WRITE = 4;

    public ClientHandler(ReadableByteChannel readableByteChannel,
                         WritableByteChannel writableByteChannel,
                         CountDownLatch latch,
                         AtomicLong sortTime,
                         int queries) {
        queriesToDo = queries;
        queriesProcessedTotal = latch;
        readableChannel = readableByteChannel;
        writableChannel = writableByteChannel;
        timeForSort = sortTime;
        state = READ_SIZE;
        size = ByteBuffer.allocate(4);
    }

    public int readSize() throws IOException {
        if(size.remaining() == 4){
            start = System.currentTimeMillis();
        }
        readableChannel.read(size);
        if (size.remaining() == 0) {
            size.flip();
            data = ByteBuffer.allocate(size.getInt());
            size.clear();
            state = READ_DATA;
        }
        return state;
    }

    public int readData() throws IOException {
        readableChannel.read(data);
        if (data.remaining() == 0) {
            data.flip();
            state = PROCESS_DATA;
        }
        return state;
    }

    public synchronized int process() {
        int[] arrayToSort = Utils.getArrayFromBytes(data.array());
        if (arrayToSort == null) {
            throw new RuntimeException("Parse exception");
        }
        long start = System.currentTimeMillis();
        sort(arrayToSort);
        timeForSort.addAndGet(System.currentTimeMillis() - start);
        byte[] res = Utils.toByteArray(arrayToSort);
        data = ByteBuffer.allocate(4 + res.length);
        data.putInt(res.length);
        data.put(res);
        data.flip();
        state = WRITE;
        return state;
    }


    public int write() throws IOException {
        writableChannel.write(data);
        if (!data.hasRemaining()) {
            data.clear();
            queriesProcessedTotal.countDown();
            queriesToDo--;
            time += (System.currentTimeMillis() - start);
            if(queriesToDo == 0){
                state = 0;
            }
            state = READ_SIZE;
        }
        return state;
    }

    public long getTime(){
        return time;
    }

    public int getState() {
        return state;
    }

    public ReadableByteChannel getReadableChannel() {
        return readableChannel;
    }

    public WritableByteChannel getWritableChannel() {
        return writableChannel;
    }

    public CountDownLatch getQueriesProcessed() {
        return queriesProcessedTotal;
    }
}
