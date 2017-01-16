import java.io.*;
import java.net.Socket;

interface MailGetter {
    //void connect(String host) throws IOException;
    void disconnect();
    //void login(String login, String password) throws IOException;
    boolean authorize(String host, String login, String password);
    void checkMail() throws IOException;
}

class POP3Client implements MailGetter {
    private Socket socket;
    private BufferedReader reader;
    private OutputStreamWriter writer;
    private int messageCount;

    private void connect(String host) throws IOException {

        socket = new Socket(host, 110);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new OutputStreamWriter(socket.getOutputStream());

        if (reader.readLine().startsWith("+OK")) {
            System.out.println("Client connected to the POP3 server");
            return;
        }

        throw new IOException();
    }

    public void disconnect() {
        try {
            reader.close();
        } catch (Exception e) {}

        try {
            writer.close();
        } catch (Exception e) {}

        try {
            socket.close();
        } catch (Exception e){}
    }

    private void login(String login, String password) throws IOException {
        writer.write(String.format("USER %s\n", login));
        writer.flush();
        if (reader.readLine().startsWith("+OK")) {
            System.out.println("Username is valid");
        } else {
            System.out.println("Username is invalid");
            throw new IOException();
        }
        writer.write(String.format("PASS %s\n", password));
        writer.flush();
        if (reader.readLine().startsWith("+OK")) {
            System.out.println("Password is valid");
            return;
        } else {
            System.out.println("Password is invalid");
            throw new IOException();
        }
    }

    public boolean authorize(String host, String login, String password) {
        try {
            connect(host);
        } catch (IOException e) {
            System.out.println("Couldn't connect to the POP3 server");
            return false;
        }

        try {
            login(login, password);
        } catch (IOException e) {
            System.out.println("Couldn't authorize");
            return false;
        }

        return true;
    }

    public void checkMail() throws IOException {
        writer.write("STAT\n");
        writer.flush();

        String[] reply = reader.readLine().split(" ");
        messageCount = Integer.parseInt(reply[1]);
        System.out.printf("You've got %d message(s)%n", messageCount);

        for (int i = 1; i <= messageCount; ++i) {
            displayMessage(i);
        }

        deleteMessages();
        disconnect();
    }

    private void displayMessage(int msg) throws IOException {
        System.out.println(msg);
        writer.write(String.format("RETR %d\n", msg));
        writer.flush();

        String reply;
        while(true) {
            reply = reader.readLine();
            System.out.println(reply);

            if (".".equals(reply)) {
                break;
            }

        }
    }

    private void deleteMessages() throws IOException {
        for (int i = 1; i <= messageCount; ++i) {
            writer.write(String.format("DELE %d\n", i));
            writer.flush();
        }

        writer.write("QUIT\n");
        writer.flush();
    }
}


public class Client {

    public static String usage = "Wrong arguments. Usage: java Client <host> <username> <password> <protocol>";

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 4) {
            throw new IllegalArgumentException(usage);
        }

        MailGetter mailGetter = null;

        if ("POP3".equalsIgnoreCase(args[3])) {
            mailGetter = new POP3Client();
        } else {
            return;
        }

        String host = args[0];
        String username = args[1];
        String password = args[2];

        while(true) {
            if (mailGetter.authorize(host, username, password)) {
                System.out.println("SUCCESS");
                try {
                    mailGetter.checkMail();
                } catch (IOException e) {
                    System.out.println("OUPS!");
                }
            } else {
                mailGetter.disconnect();
                System.out.println("FAIL");
                break;
            }

            Thread.sleep(10000);
        }

    }
}
