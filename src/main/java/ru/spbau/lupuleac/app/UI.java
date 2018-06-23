package ru.spbau.lupuleac.app;

import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.io.PrintWriter;
import java.io.StringWriter;
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

    public static void exceptionDialog(Exception ex){
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Exception Dialog");
        alert.setHeaderText("Problems while running server");
        alert.setContentText(ex.getMessage());


// Create expandable Exception.
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        String exceptionText = sw.toString();

        Label label = new Label("The exception stacktrace was:");

        TextArea textArea = new TextArea(exceptionText);
        textArea.setEditable(false);
        textArea.setWrapText(true);

        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);
        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);

        GridPane expContent = new GridPane();
        expContent.setMaxWidth(Double.MAX_VALUE);
        expContent.add(label, 0, 0);
        expContent.add(textArea, 0, 1);

// Set expandable Exception into the dialog pane.
        alert.getDialogPane().setExpandableContent(expContent);

        alert.showAndWait();
    }
}
