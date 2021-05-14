package client;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import java.io.IOException;

public class LoginController {

    @FXML
    private TextField username;
    @FXML
    private PasswordField password;

    private Mailbox model = null;
    private Stage stage = null;

    /**
     * initialize the LoginController, binds the properties with the given model
     *
     * @param model data-model of the application.
     * @param stage the stage on witch the new controller will be shown
     */
    public void init(Mailbox model,Stage stage) {
        this.model = model;
        this.stage=stage;
        model.usernameProperty().bind(username.textProperty());
        model.passwordProperty().bind(password.textProperty());
    }

    public void handleLogin() throws IOException {
        if (model.login()) {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/mailbox.fxml"));
            Pane root = loader.load();

            MailboxController controller = loader.getController();
            controller.init(model);
            this.stage.setScene(new Scene(root));
        }

    }
}
