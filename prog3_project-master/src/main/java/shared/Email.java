package shared;

import com.opencsv.CSVWriter;
import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Email is the base object of our project, can be easily created from a CSV line or created from scratch
 * implements methods such as "pretty print" or to print as CSV line
 */
public class Email implements Serializable {
    private final ArrayList<String> receivers;
    private final String subject, body, date, sender;
    private String ID;

    /**
     * create and Email from scratch, passing al the field to the constructor
     *
     * @param receivers an ArrayList of receivers
     * @param subject   string of the email subject
     * @param body      body of the email
     * @param ID        ID of the email
     * @param date      date of sending the email
     * @param sender    who sends the email
     */
    public Email(String[] receivers, String subject, String body, String date, String sender, String ID) {
        this.receivers = new ArrayList<>(Arrays.asList(receivers));
        this.subject = subject;
        this.body = body;
        this.date = date;
        this.sender = sender;
        this.ID = ID;
    }

    @Override
    public String toString() {
        return sender.split("@")[0] + "," + subject;
    }

    /**
     * writes the email as a CSV line
     * @param file FileWriter of the file to update
     */
    public void toCsv(Writer file) {
        CSVWriter writer = new CSVWriter(file);
        String receiversString = String.join(" ", receivers);
        writer.writeNext(new String[]{receiversString,subject,body,date,sender,ID});
        try {
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setID(String ID) {
        this.ID = ID;
    }

    public ArrayList<String> getReceivers() {
        return receivers;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public String getDate() {
        return date;
    }

    public String getSender() {
        return sender;
    }

    public String getID() {
        return ID;
    }
}
