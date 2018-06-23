package ru.spbau.lupuleac.app;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.Comparator;
import java.util.Map;

public class Main {
    private static int queriesPerClient;
    private static int numberOfClients;
    private static int timeInterval;
    private static int elementsInArray;
    private static int upperLimit;
    private static int step;

    //10000, 10, 10, 100
    public static void main(String[] args) {
        elementsInArray = 10000;
        numberOfClients = 10;
        queriesPerClient = 10;
        timeInterval = 50;
        upperLimit = 250;
        step = 20;
        run("multithreaded_delta", MyApplication.Design.MULTITHREADED,
                MyApplication.ChangingParameter.TIME_BETWEEN_QUERIES);
    }


    private static void run(String path, MyApplication.Design design, MyApplication.ChangingParameter parameter) {
        try {
            ServerTask serverTask = new ServerTask("192.168.0.13", design,
                    parameter, "192.168.0.12",
                    1500, 1600, queriesPerClient, numberOfClients,
                    elementsInArray, timeInterval, step, upperLimit
            );
            Map<Integer, ServerTask.TestResult> results = serverTask.call();
            processResults(path + "_sort.txt", results, 0);
            processResults(path + "_client.txt", results, 1);
            processResults(path + "_process.txt", results, 2);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void processResults(String path, Map<Integer, ServerTask.TestResult> results, int time) {
        try {
            PrintWriter writer = new PrintWriter(path, "UTF-8");
            DecimalFormat df = new DecimalFormat("#0.00");
            if(time == 0){
                results.entrySet().stream().sorted(
                        Comparator.comparingInt(Map.Entry::getKey)
                ).forEach((x -> {
                    writer.println(x.getKey() + "," + df.format(x.getValue().getSortedTime()));
                }));
                writer.flush();
            }
            if(time == 1){
                results.entrySet().stream().sorted(
                        Comparator.comparingInt(Map.Entry::getKey)
                ).forEach((x -> {
                    writer.println(x.getKey() + "," + df.format(x.getValue().getClientTime()));
                }));
                writer.flush();
            }
            if(time == 2){
                results.entrySet().stream().sorted(
                        Comparator.comparingInt(Map.Entry::getKey)
                ).forEach((x -> {
                    writer.println(x.getKey() + "," + df.format(x.getValue().getQueryTime()));
                }));
                writer.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
