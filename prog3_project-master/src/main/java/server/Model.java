package server;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;
import javafx.beans.property.SimpleStringProperty;
import shared.Email;
import shared.Message;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.lang.System.exit;

public class Model implements Runnable {
    private static final AtomicInteger mailId = new AtomicInteger();
    private final SimpleStringProperty logs;
    private ServerSocket server = null;
    private HashMap<String, String> users = null;
    private String logfilePath = null;
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private final ExecutorService exec;
    private final String configPath = "data/configuration.csv";

    public SimpleStringProperty logsProperty() {
        return logs;
    }

    // server initialization
    public Model() {
        this.logs = new SimpleStringProperty();
        exec = Executors.newFixedThreadPool(5);
        try {
            server = new ServerSocket(8080);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //set configuration variables
        try{
            CSVReader reader = new CSVReader(new FileReader(configPath));
            List<String[]> configurations = reader.readAll();
            reader.close();
            for (String[] line: configurations) {
                switch (line[0]) {
                    case "users":
                        users = loadUsers(line[1]);
                        break;
                    case "logs":
                        logfilePath = line[1];
                        break;
                    case "mailLastId":
                        mailId.set(Integer.parseInt(line[1]));
                        break;
                }
            }

            if (users == null || logfilePath==null )
                throw new CsvException("missing a configuration parameter");
        }catch (IOException | CsvException e){
            //if the config file is not correctly formatted use the default values
            System.err.println(e.getMessage() + " using the default values");
            addLogMessage(e.getMessage() + " using the default values");
            users = loadUsers("data/login.csv");
            logfilePath="data/logs.txt";
            mailId.set(0);
        }

    }

    /**
     * after everything is set up this method can be used to start the server that will then listen for incoming connection
     */
    public void run() {
        while (true) {
            try {
                exec.execute(new Connection(server.accept()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void close(){
        List<String[]> configurations;
        try  {
            //writes the last ID back into configuration file
            CSVReader reader = new CSVReader(new FileReader(configPath));
            configurations = reader.readAll();
            for (String[] config: configurations) {
                if (config[0].equals("mailLastId"))
                    config[1]= String.valueOf(mailId.getAcquire());
            }
            reader.close();
            CSVWriter writer = new CSVWriter(new FileWriter(configPath),CSVWriter.DEFAULT_SEPARATOR, CSVWriter.NO_QUOTE_CHARACTER,CSVWriter.DEFAULT_ESCAPE_CHARACTER,CSVWriter.DEFAULT_LINE_END);
            writer.writeAll(configurations);
            writer.close();
            //close all child thread
            exec.shutdown();
            if (!exec.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                exec.shutdownNow();
            }
        } catch (InterruptedException | IOException | CsvException e) {
            exec.shutdownNow();
        }

    }

    /** print a new log, on the GUI and in the text file.
     * @param message log message that needs to be shown
     */
    private synchronized void addLogMessage(String message){
        StringBuilder log = new StringBuilder();
        log.append(dtf.format(LocalDateTime.now())).append(":").append(message).append("\n");
        if (logs.getValue() != null)
            logs.setValue(logs.getValue() + log.toString());
        else
            logs.setValue(log.toString());
        try (PrintWriter printLogs = new PrintWriter(new FileOutputStream(logfilePath, true))) {
            printLogs.append(log.toString());
        } catch (IOException e) {
            e.printStackTrace(); //if the logs cannot be written the program has to be stopped
        }
    }

    /**
     * load every user in memory, so checking user information is really fast
     *
     * @param filename path of the csv in which are saved the users
     * @return an HashMap having username as key and password as value
     */
    private HashMap<String, String> loadUsers(String filename) {
        HashMap<String, String> users = new HashMap<>();

        String user, pass;
        List<String> lines = null;
        try (BufferedReader in = new BufferedReader(new FileReader(filename))) {
            lines = in.lines().collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println(e.getMessage());
            exit(1);
        }

        for (String line : lines) {
            user = line.split(",")[0].trim();
            pass = line.split(",")[1].trim();
            users.put(user,pass);
        }
        return users;
    }

    private boolean checkCredentials(String username, String passHashed) {
        if (users.containsKey(username))
            return users.get(username).equals(passHashed);
        else
            return false;
    }

    /**
     * For each new client a Connection is created, the scope of this class is to handle client requests.
     * Can be used in a thread or in a threadPool, no need to return results so it doesn't implement Callable
     */
    class Connection implements Runnable {
        Socket socket;

        public Connection(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            System.out.println("client connected from " + socket.getInetAddress() + ":" + socket.getPort());
            ObjectInputStream in;
            ObjectOutputStream out;
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());

                Message message = (Message) in.readObject();

                selectAction(message.getText(), message.getUser(), message.getHash(), in, out);

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * given an username and the last time he updated send via socket all the new mail received
         * @param username username
         * @param unixTimeLastUpdate list time the user asked for an update, expressed in UnixTimestamp
         */
        private void sendNewMails(String username,long unixTimeLastUpdate,ObjectOutputStream out){
            Email[] mailArray = null;
            List<String[]> mailList = null;
            try {
                FileInputStream csv = new FileInputStream("data/" + username + ".csv");
                //open a fileInputStream in order to use the FileLock present in that object
                try {
                    csv.getChannel().tryLock(0L, Long.MAX_VALUE, true); //shared lock for reading
                    CSVReader reader = new CSVReader(new InputStreamReader(csv));
                    mailList = reader.readAll();
                    reader.close();
                }finally {
                    csv.close();//this releases the lock
                }
                mailList = mailList.stream().filter(x -> { //convert from date as string to Epoch, easier and more secure comparison
                    ZonedDateTime date = LocalDateTime.parse(x[3], dtf)
                            .atZone(ZoneId.of("Europe/Rome"));
                    return date.toEpochSecond() >= unixTimeLastUpdate;
                }).collect(Collectors.toList()); //filters all the mail already possessed by the client, the ones received before unixTimeLastUpdate
                ArrayList<Email> tempMail = new ArrayList<>();
                //creates new mails from the CSV file. then send an array of those via socket
                for (String[] line: mailList) {
                    tempMail.add(new Email(line[0].split(" "),line[1],line[2],line[3],line[4],line[5]));
                }
                mailArray = tempMail.toArray(Email[]::new);
            } catch (IOException e) {
                e.printStackTrace();
            }catch (CsvException e) {
                System.out.println(e.getMessage());
                addLogMessage(e.getMessage());
            }
            try {
                Message mails = new Message("new mails",mailArray);
                out.writeObject(mails);
            } catch (IOException e) {
                System.err.println(e.getMessage());
                addLogMessage(e.getMessage());
            }

        }

        /**
         * every message that clients sends is composed as this: "action,username,password"
         * this function analyzes the request and act accordingly, first checking the credentials, then executing the requested action
         *
         * @param action   what action the client wants to do
         * @param username client username
         * @param password hash of client password
         */
        private void selectAction(String action, String username, String password, ObjectInputStream in, ObjectOutputStream out) throws IOException, ClassNotFoundException {
            if (!checkCredentials(username, password)) {
                Message error = new Message("wrong credentials");
                error.setError(true);
                out.writeObject(error);
                addLogMessage(username + " tried access with wrong credential from: " + socket.getInetAddress());
                return;
            }
            switch (action) {
                case "auth": {
                    Message response = new Message("Authenticated");
                    System.out.println(dtf.format(LocalDateTime.now()) + ": " + username + " Authenticated");
                    addLogMessage(username + " Authenticated from: " + socket.getInetAddress());
                    out.writeObject(response);
                    //if the user doesn't have an inbox on server create it, then send all the emails contained in the inbox
                    File f = new File("data/" + username + ".csv");
                    if (!f.exists()) {
                        try {
                            f.createNewFile();
                        } catch (IOException e) {
                            System.err.println(e.getMessage());
                            exit(1); //surely a permission problem
                        }
                    }
                    sendNewMails(username, 0, out);
                    break;
                }
                case "update": //user requested an update, send all the mail received after a given timestamp
                    String updateTimeString = ((Message) in.readObject()).getText();
                    try {
                        long timestampTemp = Long.parseLong(updateTimeString);
                        sendNewMails(username, timestampTemp, out);
                    } catch (NumberFormatException e) {
                        addLogMessage("user " + username + " requested an update with wrong timestamp, timestamp: " + updateTimeString);
                        System.err.println(e.getMessage());
                        Message error = new Message("cannot update, wrong timestamp");
                        error.setError(true);
                        out.writeObject(error);
                    } finally {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case "send": {
                    Message response;
                    try {
                        //waits for an object Email to be sent
                        Email newMail = ((Message) in.readObject()).getEmail();
                        //reads the receiver and filters them, excluding the non existing ones
                        List<String> wrongReceivers = new ArrayList<>();
                        for (String receiver : newMail.getReceivers()) {
                            if (receiver.equals(username)) { // the user is trying to send an email to himself
                                wrongReceivers.add(username);
                            }else {
                                if (users.containsKey(receiver)) {
                                    //save the email in the correct file, if the user exist
                                    newMail.setID(String.valueOf(Model.mailId.getAndIncrement()));
                                    FileOutputStream csv = new FileOutputStream("data/" + receiver + ".csv", true);
                                    try {
                                        csv.getChannel().lock();//exclusive lock for writing
                                        newMail.toCsv(new OutputStreamWriter(csv));
                                    } finally {
                                        csv.close(); // this also releases the lock
                                    }
                                } else {
                                    wrongReceivers.add(receiver);
                                }
                            }
                        }
                        //returns to the client eventual wrong users
                        if (wrongReceivers.size() != 0) {
                            response = new Message("cant find users: " + String.join(",", wrongReceivers));
                            response.setError(true);
                        } else {
                            response = new Message("status ok");
                        }
                        out.writeObject(response);
                        int sentMail = (newMail.getReceivers().size() - wrongReceivers.size());
                        addLogMessage("user " + username + " sent " + sentMail + "/" + newMail.getReceivers().size() + " email");

                    } catch (IOException | ClassNotFoundException e) {
                        addLogMessage("user " + username + " tried to send a mail, but an error occurred");
                        addLogMessage(e.getMessage());
                        System.err.println(e.getMessage());
                    }
                    break;
                }
                case "delete": {
                    Message response;
                    List<String[]> toMaintain = null;
                    try {
                        Message msg = (Message) in.readObject();
                        //listens fot the IDs of the mails that the client wants to delete
                        ArrayList<String> IDs = new ArrayList<>(Arrays.asList(msg.getText().split(",")));
                        FileInputStream csv = new FileInputStream("data/" + username + ".csv");
                        //open a fileInputStream in order to use the FileLock present in that object
                        try {
                            csv.getChannel().tryLock(0L, Long.MAX_VALUE, true); //shared lock for reading
                            CSVReader reader = new CSVReader(new InputStreamReader(csv));
                            //reads the csv file without the emails that needs to be deleted
                            toMaintain = reader.readAll().stream().filter(x -> !IDs.contains(x[5])).collect(Collectors.toList());
                            reader.close();
                        }finally {
                            csv.close();//this releases the lock
                        }

                        //writes te list back to the csv
                        FileOutputStream csvWrite = new FileOutputStream("data/" + username + ".csv", true);
                        try {
                            csvWrite.getChannel().lock();//exclusive lock for writing
                            CSVWriter writer = new CSVWriter(new OutputStreamWriter(csvWrite));
                            writer.writeAll(toMaintain);
                            response = new Message("status ok");
                            out.writeObject(response);
                            writer.close();
                        } finally {
                            csvWrite.close(); // this also releases the lock
                        }

                        addLogMessage("users " + username + " deleted " + IDs.size() + " emails");
                    } catch (IOException | NullPointerException |CsvException e) {
                        System.out.println(e.getMessage());
                        addLogMessage(e.getMessage());
                        response = new Message("cannot delete requested emails, wrong IDs");
                        response.setError(true);
                        out.writeObject(response);
                    }
                    break;
                }
            }

        }
    }

}
