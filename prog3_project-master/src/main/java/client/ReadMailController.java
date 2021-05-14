package client;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;

import java.io.IOException;

public class ReadMailController {
    Mailbox model = null;

    @FXML
    private TextField To;
    @FXML
    private TextField From;
    @FXML
    private TextField Subject;
    @FXML
    private TextArea Body;
    private Pane view;
    private String mailID;
    NewMailController controller;
    Pane newMail;

    /**
     * delete button pressed, when viewing a mail
     */
    @FXML
    public void handleDelete() {
        model.delete(new String[]{mailID}, false);
        //close this view, go back to the Mailbox view
        view.getChildren().clear();
    }

    /**
     * forward button pressed, when viewing a mail
     */
    @FXML
    public void handleForward() {
        //reinitialize the controller tho change information shown in the view
        controller.init(model, view);
        //delete the previous view and place the new one in it's place
        view.getChildren().clear();
        view.getChildren().add(newMail);
        model.forward(mailID);
    }

    /**
     * reply button pressed, when viewing a mail
     */
    @FXML
    public void handleReply() {
        //reinitialize the controller tho change information shown in the view
        controller.init(model, view);

        //delete the previous view and place the new one in it's place
        view.getChildren().clear();
        view.getChildren().add(newMail);
        model.reply(mailID, false);
    }

    /**
     * reply all button pressed, when viewing a mail
     */
    @FXML
    public void handleReplyAll() {
        //reinitialize the controller tho change information shown in the view
        controller.init(model, view);

        //delete the previous view and place the new one in it's place
        view.getChildren().clear();
        view.getChildren().add(newMail);
        model.reply(mailID, true);
    }

    public void init(Mailbox model, String ID, Pane view) throws IOException {
        this.model = model;
        this.mailID = ID;
        this.view = view;
        //unbind the properties from the last controller (could be bound to a newMailController)
        model.receiversProperty().unbind();
        model.senderProperty().unbind();
        model.subjectProperty().unbind();
        model.bodyProperty().unbind();

        //bind the properties tho the controller
        To.textProperty().bind(model.receiversProperty());
        From.textProperty().bind(model.senderProperty());
        Subject.textProperty().bind(model.subjectProperty());
        Body.textProperty().bind(model.bodyProperty());

        //instruct the model to set the properties right for the Mail selected (the one that needs to be shown)
        model.read(ID);

        //load view and controller from the XML file
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/mail_interface.fxml"));
        newMail = loader.load();
        controller = loader.getController();
    }
}
