package client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception{
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/login.fxml"));
        Pane root = loader.load();

        LoginController controller = loader.getController();
        controller.init(new Mailbox(),primaryStage);

        primaryStage.setTitle("Moji");
        //TODO: look up the Scene Class in JavaFX for layout problem
        primaryStage.setResizable(false);
        primaryStage.setScene(new Scene(root, 600, 400));
        primaryStage.show();
    }


    public static void main(String[] args) {
        launch(args);
    }
}
