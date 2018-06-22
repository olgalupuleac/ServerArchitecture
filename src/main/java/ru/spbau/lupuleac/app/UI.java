package ru.spbau.lupuleac.app;

import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;

import java.util.Map;

public class UI {
    public static TabPane createSeries(Map<Integer, ServerTask.TestResult> testResults,
                                       MyApplication.ChangingParameter parameter) {
        XYChart.Series<Number, Number>[] charts = new XYChart.Series[3];
        for(int i = 0; i < 3; i++){
            charts[i] = new XYChart.Series<>();
        }
        for (Map.Entry<Integer, ServerTask.TestResult> result : testResults.entrySet()) {
            charts[0].getData().add(new XYChart.Data<>(result.getKey(), result.getValue().getClientTime()));
            charts[1].getData().add(new XYChart.Data<>(result.getKey(), result.getValue().getSortedTime()));
            charts[2].getData().add(new XYChart.Data<>(result.getKey(), result.getValue().getQueryTime()));
        }
        TabPane tabPane = new TabPane();
        String[] tabName = {"Client time", "Sort Time", "Process Time"};
        for (int i = 0; i < 3; ++i) {
            NumberAxis xAxis = new NumberAxis();
            NumberAxis yAxis = new NumberAxis();
            xAxis.setLabel(parameter.toString());
            yAxis.setLabel("Time");
            Tab tab = new Tab(tabName[i]);
            LineChart<Number, Number> lineChart = new LineChart<>(xAxis, yAxis);
            lineChart.getData().addAll(charts[i]);
            tab.setContent(lineChart);
            tabPane.getTabs().add(tab);
        }
        return tabPane;
    }

    public void progressBar(){
        final Slider slider = new Slider();
        slider.setMin(0);
        slider.setMax(50);

        final ProgressBar pb = new ProgressBar(0);
        final ProgressIndicator pi = new ProgressIndicator(0);

        slider.valueProperty().addListener((ov, old_val, new_val) -> {
            pb.setProgress(new_val.doubleValue()/50);
            pi.setProgress(new_val.doubleValue()/50);
        });
    }
}
