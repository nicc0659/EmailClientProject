package client;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;


public class NewMailController {
    Mailbox model = null;

    @FXML
    private TextField To;
    @FXML
    private TextField Subject;
    @FXML
    private TextArea Body;

    private Pane view;

    public void init(Mailbox model, Pane view) {
        this.model = model;
        this.view = view;

        model.receiversProperty().bindBidirectional(To.textProperty());
        model.subjectProperty().bindBidirectional(Subject.textProperty());
        model.bodyProperty().bindBidirectional(Body.textProperty());
    }

    //TODO: if we'we got time we should add a draft feature

    public void handleSend() {
        model.sendMail();
        view.getChildren().clear();//it "hides the display" of the previous view
    }
}
