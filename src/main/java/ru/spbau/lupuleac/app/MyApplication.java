package ru.spbau.lupuleac.app;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.Map;


public class MyApplication extends Application {
    private Stage window;
    private static int portForServer;
    private static int clientPort;
    private static String clientManagerHost;
    private static String host;
    private int numberOfClients;
    private int queriesPerClient;
    private int timeInterval;
    private int numberOfElementsInArray;
    private int step;
    private int upperLimit;
    private Design designToTest;
    private ChangingParameter changingParameter;
    public static void main(String[] args){
        if(args.length != 4){
            System.err.println("Incorrect usage: first argument - port number send information to clients," +
                    " second - client host name, third port number for host, "
            + "fourth - server host name");
        }
        clientPort = Integer.parseInt(args[0]);
        clientManagerHost = args[1];
        portForServer = Integer.parseInt(args[2]);
        host = args[3];
        launch(args);
    }

    //@Override
    public void start(Stage primaryStage) throws Exception {
        window = primaryStage;
        primaryStage.setMinHeight(300);
        primaryStage.setMinWidth(300);
        primaryStage.setScene(new Scene(chooseDesign()));
        primaryStage.show();

    }

    private GridPane chooseDesign(){
        //Creating a GridPane container
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10, 10, 10, 10));
        grid.setVgap(5);
        grid.setHgap(5);
        ComboBox design = new ComboBox(FXCollections.observableArrayList(
                "Multi-threaded", "Blocking", "Non-blocking")
        );
        design.setPromptText("Design to test");
        GridPane.setConstraints(design, 0, 0);
        grid.getChildren().add(design);
        final TextField queriesPerClient = new TextField();
        queriesPerClient.setPromptText("Enter a number of queries per client.");
        GridPane.setConstraints(queriesPerClient, 0, 1);
        grid.getChildren().add(queriesPerClient);

        ComboBox parameter = new ComboBox(FXCollections.observableArrayList(
                "Number of elements in array", "Number of clients", "Time between queries")
        );
        parameter.setPromptText("Changing parameter");
        GridPane.setConstraints(parameter, 0, 2);
        grid.getChildren().add(parameter);
//Defining the Submit button
        Button submit = new Button("Submit");
        GridPane.setConstraints(submit, 1, 0);
        grid.getChildren().add(submit);
//Defining the Clear button
        //Adding a Label
        final Label label = new Label();
        label.setTextFill(Color.RED);
        GridPane.setConstraints(label, 0, 3);
        GridPane.setColumnSpan(label, 2);
        grid.getChildren().add(label);

//Setting an action for the Submit button
        submit.setOnAction(new EventHandler<ActionEvent>() {

            @Override
            public void handle(ActionEvent e) {
                if(design.getSelectionModel().getSelectedItem() == null){
                    label.setText("Choose the design to test");
                    return;
                }
                if(queriesPerClient.getText() == null || queriesPerClient.getText().isEmpty()){
                    label.setText("Enter a number of queries per client");
                    return;
                }
                if(parameter.getSelectionModel().getSelectedItem() == null){
                    label.setText("Choose the parameter to be changed in tests");
                    return;
                }
                try{
                    int x = Integer.parseInt(queriesPerClient.getText());
                    setDesignToTest(design.getSelectionModel().getSelectedIndex());
                    setChangingParameter(parameter.getSelectionModel().getSelectedIndex());
                    if(x <= 0){
                        label.setText("Number of queries should be positive");
                        return;
                    }
                    MyApplication.this.queriesPerClient = x;
                    window.setScene(new Scene(otherConstantsAndRange()));
                } catch (NumberFormatException exception) {
                    label.setText("Number of queries should be an integer");
                }
            }
        });
        return grid;
    }

    void setDesignToTest(int ind){
        designToTest = Design.values()[ind];
    }

    void setChangingParameter(int ind){
        changingParameter = ChangingParameter.values()[ind];
    }

    GridPane otherConstantsAndRange(){
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10, 10, 10, 10));
        grid.setVgap(7);
        grid.setHgap(7);
        final TextField elementsInArrayTF = new TextField();
        elementsInArrayTF.setPromptText("Number of elements");
        GridPane.setConstraints(elementsInArrayTF, 0, 0);
        grid.getChildren().add(elementsInArrayTF);

        final TextField numberOfClientsTF = new TextField();
        numberOfClientsTF.setPromptText("Number of clients");
        GridPane.setConstraints(numberOfClientsTF, 0, 1);
        grid.getChildren().add(numberOfClientsTF);

        final TextField timeIntervalTF = new TextField();
        timeIntervalTF.setPromptText("Time interval");
        GridPane.setConstraints(timeIntervalTF, 0, 2);
        grid.getChildren().add(timeIntervalTF);

        final TextField stepTF = new TextField();
        stepTF.setPromptText("Step");
        GridPane.setConstraints(stepTF, 0, 3);
        grid.getChildren().add(stepTF);

        final TextField upperLimitTF = new TextField();
        upperLimitTF.setPromptText("Upper limit");
        GridPane.setConstraints(upperLimitTF, 0, 4);
        grid.getChildren().add(upperLimitTF);

        Button submit = new Button("Submit");
        GridPane.setConstraints(submit, 1, 0);
        grid.getChildren().add(submit);
//Defining the Clear button
        //Adding a Label
        final Label label = new Label();
        label.setText("Enter initial values and step and upper limit\n for changing parameter");
        GridPane.setConstraints(label, 0, 5);
        GridPane.setColumnSpan(label, 2);
        grid.getChildren().add(label);

//Setting an action for the Submit button
        submit.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                if(numberOfClientsTF.getText() == null || numberOfClientsTF.getText().isEmpty()
                        || elementsInArrayTF.getText() == null || elementsInArrayTF.getText().isEmpty()
                        || timeIntervalTF.getText() == null || timeIntervalTF.getText().isEmpty()
                        || upperLimitTF.getText() == null || upperLimitTF.getText().isEmpty()
                        || stepTF.getText() == null || stepTF.getText().isEmpty()){
                    label.setTextFill(Color.RED);
                    label.setText("Enter all values");
                    return;
                }
                try {
                    numberOfClients = Integer.parseInt(numberOfClientsTF.getText());
                    numberOfElementsInArray = Integer.parseInt(elementsInArrayTF.getText());
                    timeInterval = Integer.parseInt(timeIntervalTF.getText());
                    step = Integer.parseInt(stepTF.getText());
                    upperLimit = Integer.parseInt(upperLimitTF.getText());
                    ServerTask serverTask = new ServerTask(clientManagerHost,
                            designToTest, changingParameter,
                            host, clientPort, portForServer, queriesPerClient,
                            numberOfClients, numberOfElementsInArray, timeInterval, step, upperLimit);
                    Map<Integer, ServerTask.TestResult> results = serverTask.call();
                    window.setScene(new Scene(UI.createSeries(results, changingParameter), 800, 600));
                    window.setTitle("Tests results");
                    window.show();
                }catch (Exception ex){
                    ex.printStackTrace();
                    //TODO incorrect input
                }
            }
        });
        return grid;
    }



    public enum Design {
        MULTITHREADED,
        BLOCKING,
        NONBLOCKING
    }

    public enum ChangingParameter {
        NUMBER_OF_ELEMENTS_IN_ARRAY,
        NUMBER_OF_CLIENTS,
        TIME_BETWEEN_QUERIES;

        @Override
        public String toString() {
            switch (this){
                case NUMBER_OF_ELEMENTS_IN_ARRAY:
                    return "Number of elements in array";
                case TIME_BETWEEN_QUERIES:
                    return "Time interval between queries";
                case NUMBER_OF_CLIENTS:
                    return "Number of clients";
            }
            return super.toString();
        }
    }

}
