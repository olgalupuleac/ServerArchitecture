package ru.spbau.lupuleac.server;

import java.io.*;
import java.util.Random;

public class Utils {
    public static int[] generateArray(int n) {
        int[] res = new int[n];
        Random random = new Random(System.currentTimeMillis());
        for (int i = 0; i < n; i++) {
            res[i] = random.nextInt();
        }
        return res;
    }

    public static void sendArray(int[] array, DataOutputStream out) throws IOException {
        ArrayProtocol.Array.Builder builder = ArrayProtocol.Array.newBuilder();
        for (int x : array) {
            builder.addData(x);
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        builder.build().writeDelimitedTo(outputStream);
        System.err.println(outputStream.toByteArray().length);
        out.writeInt(outputStream.toByteArray().length);
        builder.build().writeDelimitedTo(out);
    }

    public static int[] getArray(DataInputStream in) throws IOException {
        ArrayProtocol.Array array = ArrayProtocol.Array.parseDelimitedFrom(in);
        return array.getDataList().stream().mapToInt(x -> x).toArray();
    }

    public static boolean isSorted(int[] a) {
        for (int i = 1; i < a.length; i++) {
            if (a[i - 1] > a[i]) {
                return false;
            }
        }
        return true;
    }

    public static int[] getArrayFromBytes(byte[] array) {
        try {
            ArrayProtocol.Array a = ArrayProtocol.Array.parseDelimitedFrom(new ByteArrayInputStream(array));
            return a.getDataList().stream().mapToInt(x -> x).toArray();
        } catch (IOException e) {
            return null;
        }
    }

    public static byte[] toByteArray(int[] array) {
        ArrayProtocol.Array.Builder builder = ArrayProtocol.Array.newBuilder();
        for (int x : array) {
            builder.addData(x);
        }
        return builder.build().toByteArray();
    }

    public static void sort(int[] a) {
        int n = a.length;
        for (int i = 0; i < n - 1; i++) {
            int min_ind = i;
            for (int j = i + 1; j < n; j++){
                if (a[j] < a[min_ind]){
                    min_ind = j;
                }
            }
            int tmp = a[min_ind];
            a[min_ind] = a[i];
            a[i] = tmp;
        }
    }
}
