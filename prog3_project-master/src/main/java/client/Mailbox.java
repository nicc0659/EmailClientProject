package client;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import shared.Email;
import shared.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

/**
 * data-model to visualize inbox, log in and send new E-mail
 */
public class Mailbox {
    private ObservableList<Email> received = null;
    private final SimpleStringProperty receivers;
    private final SimpleStringProperty subject;
    private final SimpleStringProperty body;
    private final SimpleStringProperty sender;
    private final SimpleStringProperty username;
    private final SimpleStringProperty password;
    private Socket connection = null;
    private long lastUpdateEpoch;
    private String hash = null;
    private ObjectProperty<Image> onlineStatus = null;
    private ArrayList<Email> sendingQueue = null;
    private ArrayList<String> deletionQueue = null;

    public Mailbox() {
        username = new SimpleStringProperty();
        receivers = new SimpleStringProperty();
        subject = new SimpleStringProperty();
        body = new SimpleStringProperty();
        sender = new SimpleStringProperty();
        password = new SimpleStringProperty();
        received = FXCollections.observableArrayList();
        onlineStatus = new SimpleObjectProperty<>();
        sendingQueue = new ArrayList<>();
        deletionQueue = new ArrayList<>();
    }

    //start login
    /**
     * never ever, ever store cleartext password.
     *
     * @param password password to be hashed in SHA-256
     * @return the digest of password
     */
    private String hashPass(String password) {
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        byte[] hashInBytes = md.digest(password.getBytes());

        //bytes to hex
        StringBuilder sb = new StringBuilder();
        for (byte b : hashInBytes) {
            sb.append(String.format("%02x", b));
        }
        String passwordHashed = sb.toString();
        return passwordHashed;
    }

    /**
     * @return if the server recognized credentials
     */
    public boolean login() {
        hash = hashPass(password.get());
        Timer updateTimer = new Timer(true);
        ObjectInputStream in = null;
        ObjectOutputStream out = null;
        try {
            connection = new Socket("127.0.0.1", 8080);
            System.out.println("connected to " + connection.toString());
            try {
                in = new ObjectInputStream(connection.getInputStream());
                out = new ObjectOutputStream(connection.getOutputStream());

                out.writeObject(new Message(getUsername(), hash, "auth"));
                if (!((Message) in.readObject()).isError()){
                    //wait for the emails stored in the server
                    try {
                        received.addAll(((Message) in.readObject()).getEmails());
                        onlineStatus.setValue(new Image("img/status-online.png"));
                        lastUpdateEpoch = System.currentTimeMillis() / 1000L;
                        updateTimer.schedule(new Updater(), 10000, 10000);

                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                    return true;
                }else {
                    System.err.println("bad credentials");
                    return false;
                }

            } catch (IOException e) {
                errorPopUp("cannot establish connection with the server");
                System.err.println("cannot establish connection with the server\n" + e.getMessage());
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } finally {
                if (in != null && out != null) {
                    in.close();
                    out.close();
                }
                connection.close();
            }
        } catch (IOException e) {
            errorPopUp("cannot establish connection with the server");
            System.err.println("cannot establish connection with the server\n" + e.getMessage());
        }
        return false;
    }
    //end login

    //start inbox action

    /**
     * ask the server for every new mail arrived to your inbox after your last update, automaticaly updates the time of your last update
     *
     * @return
     */
    public boolean update() {
        boolean updated;
        Message response;
        try {
            //open a new connection and ask the server for an update
            connection = new Socket("127.0.0.1", 8080);
            ObjectInputStream in = new ObjectInputStream(connection.getInputStream());
            ObjectOutputStream out = new ObjectOutputStream(connection.getOutputStream());
            out.writeObject(new Message(getUsername(), hash, "update"));
            out.writeObject(new Message(getUsername(), hash, String.valueOf(lastUpdateEpoch)));
            try {
                //analyze the response, if the response is an error or it's missing show to the user the offline status
                response = (Message) in.readObject();
                if (response.isError())
                    errorPopUp(response.getText());
                else{
                    received.addAll(response.getEmails());
                    onlineStatus.setValue(new Image("img/status-online.png"));
                    //update the time of the last successful update
                    lastUpdateEpoch = System.currentTimeMillis() / 1000L;
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            updated=true;
        } catch (IOException e) {
            //server unreachable
            System.err.println("tried update " + e.getMessage());
            onlineStatus.setValue(new Image("img/status-offline.png"));
            updated=false;
        }finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (IOException e) {
                    System.err.println("cannot close connection with the server\n" + e.getMessage());
                }
            }
        }
        return updated;
    }

    /**
     * set the properties (receivers, body, sender and subject) to the value presents in the email (if the email exists) which id is specified as a parameter
     *
     * @param id the id of the mail that needs to be shown
     */
    public void read(String id) {
        Email selected = null;
        //find the email
        for (Email email : received) {
            if (email.getID().equals(id)) {
                selected = email;
                break;
            }
        }
        if (selected == null) //mail doesn't exist
            return;

        if (!selected.getReceivers().contains(username.getValue()))
            selected.getReceivers().add(username.getValue());
        //mail exist, set the properties
        receivers.setValue(String.join(" ", selected.getReceivers()));
        body.setValue(selected.getBody());
        sender.setValue(selected.getSender());
        subject.setValue(selected.getSubject());
    }

    /**
     * prepare an email for forwarding (if existing)
     * prepare the properties receiver, subject and body accordingly to what's in the email specified
     * the properties needs to be bound to elements in a view
     *
     * @param id the id of the mail that needs to be forwarded
     */
    public void forward(String id) {
        Email selected = null;
        for (Email email : received) {
            if (email.getID().equals(id)) {
                selected = email;
                break;
            }
        }
        if (selected == null)
            return;
        subject.setValue("forward: " + selected.getSubject());
        body.setValue("mail fowarded from " + selected.getSender() + "\n=======================\n" + selected.getBody());
    }

    /**
     * reply to a mail
     * prepare the properties receiver, subject and body accordingly to what's in the email specified
     * the properties needs to be bound to elements in a view
     *
     * @param id  the id if the email
     * @param all specifies if the client wants to reply to the sender or to every receiver (reply all)
     */
    public void reply(String id, boolean all) {
        Email selected = null;
        for (Email email : received) {
            if (email.getID().equals(id)) {
                selected = email;
                break;
            }
        }
        if (selected == null)
            return;
        //the email exist and now is in the variable selected

        selected.getReceivers().remove(username.getValue()); //remove the username from receiver list, we don't want to send mail to ourself
        String replyTo = sender.getValue();
        if (all) {
            // add to the sender field every receiver of the email (excluding the user that is reading the email) plus the sender of the original email
            replyTo += " " + String.join(" ", selected.getReceivers());
        }
        receivers.setValue(replyTo);
        subject.setValue("reply: " + selected.getSubject());
        body.setValue(selected.getSender() + " in date " + selected.getDate() + " wrote\n\n" + selected.getBody());
    }

    /**
     * delete one or more Emails
     *
     * @param ids          an array of ids of the emails
     * @param reconnection is this the first time the program is trying to delete the mail? or this is an automatic reconnection?
     */
    public void delete(String[] ids, boolean reconnection) {
        ArrayList<String> idList = new ArrayList<>(Arrays.asList(ids));
        if (!reconnection)  // the mail can't be deleted during a reconnection because the thread is not a javaFX component
            received.removeIf(x -> idList.contains(x.getID()));
        try {
            //connect to the server and send a message with all the IDs of the mail you want to delete
            connection = new Socket("127.0.0.1", 8080);
            ObjectInputStream in = new ObjectInputStream(connection.getInputStream());
            ObjectOutputStream out = new ObjectOutputStream(connection.getOutputStream());
            out.writeObject(new Message(getUsername(), hash, "delete"));
            out.writeObject(new Message(getUsername(), hash, String.join(",", ids)));

            //if the message is an error surely the problem is in the IDs sent, throw a exception, else remove those mail from the model
            if (((Message) in.readObject()).isError() && !reconnection)//if it's a reconnection we don't want any error message
                throw new IOException("something went wrong with the IDs");


            if (reconnection)
                deletionQueue.clear();

        } catch (IOException e) {
            //server is unreachable
            System.err.println(e.getMessage());
            if (!reconnection) {
                errorPopUp("cannot establish connection with the server, your action will be performed when reconnected");
                deletionQueue.addAll(idList);
            }
            onlineStatus.setValue(new Image("img/status-offline.png"));
            //add IDs to the deletion queue, will be deleted when reconnected
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (IOException e) {
                    System.err.println("cannot close connection with the server\n" + e.getMessage());
                }
            }
        }

    }
    //end inbox action

    /**
     * send a new mail which value are written in the relative properties (body, subject, receiver)
     *
     * @return boolean stating if the email was sent
     */
    public boolean sendMail() {
        //check if every field is filled
        if (getBody().equals("")) {
            errorPopUp("empty body, cannot send");
            return false;
        }
        String[] addresses = checkAddresses();
        if (addresses.length == 0) {
            errorPopUp("no valid address to send, cannot send");
            return false;
        }

        //prepare a DTF to translate from Epoch to a date format human readable
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        Email email = new Email(addresses, getSubject(), getBody(), dtf.format(LocalDateTime.now()), getUsername(), ""); // create a new email from the information presents in the properties

        ObjectOutputStream out = null;
        ObjectInputStream in = null;
        String hash = hashPass(password.get());
        boolean send = true;
        try {
            //open a new connection and prepare the server to receive the mail (first put it in a message)
            connection = new Socket("127.0.0.1", 8080);
            System.out.println("connected to " + connection.toString() + "to send a new mail");
            in = new ObjectInputStream(connection.getInputStream());
            out = new ObjectOutputStream(connection.getOutputStream());
            out.writeObject(new Message(getUsername(), hash, "send"));

            // put the email inside a message and send it
            out.writeObject(new Message(getUsername(), hash, "send", email));
            Message response = (Message) in.readObject();
            if (response.isError()) {
                //the client tried to send email to unknown users.
                send = false;
                errorPopUp(response.getText() + "\nother eventual recipient have received the email");
            }
        } catch (IOException e){
            System.err.println("cannot establish connection with the server\n" + e.getMessage());
            errorPopUp("cannot establish connection with the server");
            onlineStatus.setValue(new Image("img/status-offline.png"));
            //add the email to rhe resending queue
            sendingQueue.add(email);

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (connection != null){
                try {
                    if (in != null && out != null) {
                        in.close();
                        out.close();
                    }
                    connection.close();
                } catch (IOException | NullPointerException e) {
                    System.err.println("cannot close connection with the server\n" + e.getMessage());
                }
            }
        }
        return send;
    }

    public void sendMails(Email [] emails, boolean reconnection){
        int errors = 0;
        ObjectOutputStream out = null;
        ObjectInputStream in = null;
        String hash = hashPass(password.get());
        try {
            //open a new connection and prepare the server to receive the mail (first put it in a message)
            connection = new Socket("127.0.0.1", 8080);
            System.out.println("connected to " + connection.toString() + "to send a new mail");
            in = new ObjectInputStream(connection.getInputStream());
            out = new ObjectOutputStream(connection.getOutputStream());

            for (Email email: emails) {
                out.writeObject(new Message(getUsername(), hash, "send",email));
                out.writeObject(new Message(getUsername(), hash, "send", email));
                Message response = (Message) in.readObject();

                sendingQueue.remove(email);
                if (response.isError()) {
                    //the client tried to send email to unknown users.
                    errors ++;
                }
            }

            if (errors != 0){
                errorPopUp(errors + " mails couldn't be when the server came back online, unknown email address");
            }

        } catch (IOException e){
            if (!reconnection) {
                System.err.println("cannot establish connection with the server\n" + e.getMessage());
                errorPopUp("cannot establish connection with the server");
            }
            onlineStatus.setValue(new Image("img/status-offline.png"));

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (connection != null){
                try {
                    if (in != null && out != null) {
                        in.close();
                        out.close();
                    }
                    connection.close();
                } catch (IOException | NullPointerException e) {
                    System.err.println("cannot close connection with the server\n" + e.getMessage());
                }
            }
        }
    }

    /**
     * shows an error popUp, stop the app until ok button is clicked
     *
     * @param errorMsg the error message
     */
    private void errorPopUp(String errorMsg) {
        System.out.println(errorMsg);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(errorMsg);
        alert.showAndWait();
    }

    /**
     * regex control on the sender property
     *
     * @return true if the user has written a valid email address
     */
    private String[] checkAddresses() {
        String[] addresses = getReceivers().split(" ");
        return Arrays.stream(addresses).map(String::toLowerCase)
                .filter(x -> x.matches("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,6}$"))
                .toArray(String[]::new);
        //sorry for this, this is a stream. I learned to use them and i found it very handy. just test it and trust it.
    }
    //end mail sending

    public String getUsername() {
        return username.get();
    }

    public ObservableList<Email> getReceived() {
        return received;
    }

    public SimpleStringProperty usernameProperty() {
        return username;
    }

    public SimpleStringProperty passwordProperty() {
        return password;
    }

    public String getReceivers() {
        return receivers.get();
    }

    public SimpleStringProperty receiversProperty() {
        return receivers;
    }

    public String getSubject() {
        return subject.get();
    }

    public SimpleStringProperty subjectProperty() {
        return subject;
    }

    public String getBody() {
        return body.get();
    }

    public SimpleStringProperty bodyProperty() {
        return body;
    }

    public Image getOnlineStatus() {
        return onlineStatus.get();
    }

    public ObjectProperty<Image> onlineStatusProperty() {
        return onlineStatus;
    }

    public String getSender() {
        return sender.get();
    }

    public SimpleStringProperty senderProperty() {
        return sender;
    }

    class Updater extends TimerTask {
        @Override
        public void run() {
            Platform.runLater(new Runnable() { //runLater is necessary to make changes to the view from a non-JavaFX thread
                public void run() {
                    boolean updated = update();
                    /*in pratica se il client riesce a fare un update (ecco perchè la variabile updated) controlla che non ci siano mail da inviare
                     * o mail da eliminare in coda, se ci sono preoccupati di lanciare i due metodi delete e sendMail con il boolean reconnection = true
                     * A questo punto se hanno successo pulisci le due code. Se le due code sono vuote.....non fare nulla*/
                    if (updated) {
                        if (deletionQueue.size() > 0)
                            delete(deletionQueue.toArray(String[]::new), true);
                        if (sendingQueue.size() > 0)
                            sendMails(sendingQueue.toArray(Email[]::new), true);
                    }
                }
            });
        }
    }
}
