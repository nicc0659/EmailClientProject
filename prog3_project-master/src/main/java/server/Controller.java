package server;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

public class Controller {

    @FXML private TextArea logArea;

    public void init(Model model, Stage stage) {
        //when the user clicks the x button to close the program, the close() method is executed before this happens
        stage.setOnCloseRequest(event -> model.close() );
        logArea.textProperty().bind(model.logsProperty());

        //model is threaded to permit launch at startup
        Thread modelThread = new Thread(model);
        modelThread.setDaemon(true); // so when the GUI is closed every thread is killed
        modelThread.start();
    }


}
