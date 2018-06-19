package ru.spbau.lupuleac.app;

import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import java.util.List;
import java.util.Map;

public class UI {
    public static TabPane createSeries(Map<Integer, Logic.TestResult> testResults) {
        //noinspection unchecked
        XYChart.Series<Number, Number>[] charts = new XYChart.Series[] {
                new XYChart.Series<>(),
                new XYChart.Series<>(),
                new XYChart.Series<>()
        };
        for (Map.Entry<Integer, Logic.TestResult> result : testResults.entrySet()) {
            charts[0].getData().add(new XYChart.Data<>(result.getKey(), result.getValue().getClientTime()));
            charts[1].getData().add(new XYChart.Data<>(result.getKey(), result.getValue().getSortedTime()));
            charts[2].getData().add(new XYChart.Data<>(result.getKey(), result.getValue().getQueryTime()));
        }
        TabPane tabPane = new TabPane();
        String[] tabName = {"Время сортировки", "Время обработки", "Время клиента"};
        for (int i = 0; i < 3; ++i) {
            NumberAxis xAxis = new NumberAxis();
            NumberAxis yAxis = new NumberAxis();
            xAxis.setLabel("Задержка");
            yAxis.setLabel("Время");
            Tab tab = new Tab(tabName[i]);
            LineChart<Number, Number> lineChart = new LineChart<>(xAxis, yAxis);
            //noinspection unchecked
            lineChart.getData().addAll(charts[i]);
            tab.setContent(lineChart);
            tabPane.getTabs().add(tab);
        }
        return tabPane;
    }
}
