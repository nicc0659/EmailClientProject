package client;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import shared.Email;

import java.io.IOException;

public class MailboxController {
    private Mailbox model = null;
    private ReadMailController controller = null;

    @FXML
    private ListView<Email> mailList;
    @FXML
    private ImageView onlineStatus;
    @FXML
    private Pane readOrSend;
    private Pane incomingMail;

    /**
     * new Mail button pressed
     */
    @FXML
    private void handleNewMail() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/mail_interface.fxml"));
        Pane newMail = loader.load();

        NewMailController controller = loader.getController();
        controller.init(model, readOrSend);
        readOrSend.getChildren().clear();
        readOrSend.getChildren().add(newMail);
    }

    /**
     * delete button pressed, when selecting a mail from the menu
     */
    @FXML
    private void handleDelete() {
        readOrSend.getChildren().clear();
        if (mailList.getSelectionModel().getSelectedItems().size() == 0)
            return; //no item selected, do nothing
        model.delete(mailList.getSelectionModel().getSelectedItems().stream().map(Email::getID).toArray(String[]::new), false);
        //another stream, gets all the IDs from selected mail and passes them to model.delete as an array
    }

    /**
     * forward button pressed, when selecting a mail from the menu
     */
    @FXML
    private void handleForward() throws IOException {
        readOrSend.getChildren().clear();
        if (mailList.getSelectionModel().getSelectedItems().size() == 0)
            return;//no item selected, do nothing
        String id = mailList.getSelectionModel().getSelectedItem().getID(); //in this case the only id returned is the one of the last mail selected
        handleNewMail();
        model.forward(id);
    }

    /**
     * clicked on a Email,therefore the new view needs to be loaded
     */
    @FXML
    public void handleRead() throws IOException {
        if (mailList.getSelectionModel().getSelectedItems().size() == 0)
            return;//no item selected, do nothing

        controller.init(model, mailList.getSelectionModel().getSelectedItem().getID(), readOrSend);
        readOrSend.getChildren().clear();//it "hides the display" of the previous view
        readOrSend.getChildren().add(incomingMail);//load the new view
    }


    public void init(Mailbox model) {
        this.model = model;

        mailList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE); //allows you to select multiple emails at the same time
        mailList.setItems(model.getReceived());//takes the list of emails received and shows them
        onlineStatus.imageProperty().bind(model.onlineStatusProperty());

        //loads the EmailReaderController and loads the newMail Pane, the view doesn't have to be created every time the user wants to read a mail
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/mail_incoming.fxml"));
        try {
            incomingMail = loader.load();
            controller = loader.getController();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }
}
