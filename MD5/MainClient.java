import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.Vector;

class Client {
    private InetSocketAddress serverAddress;
    private UUID uuid;

    private int currentLength;
    private String currentPreffix;
    private String md5;

    Client(String serverIP, int serverPort) {
        this.serverAddress = new InetSocketAddress(serverIP, serverPort);
        this.uuid = UUID.randomUUID();
        System.out.println("UUID: " + uuid.toString());
        this.currentLength = 0;
        this.currentPreffix = "";
    }

    public void searchMD5() throws IOException, NoSuchAlgorithmException {
        SocketChannel socketChannel = SocketChannel.open(serverAddress);
        socketChannel.write(ByteBuffer.wrap("FIRST".concat(uuid.toString()).getBytes("UTF-8")));

        while(true) {
            if (!readAnswer(socketChannel)) {
                System.out.println("No tasks available");
                break;
            } else {
                String res = checkMD5();
                socketChannel = SocketChannel.open(serverAddress);
                if (res == null) {
                    System.out.println("No string with given md5 in this range");
                    socketChannel.write(ByteBuffer.wrap("AGAINMISS".concat(uuid.toString()).getBytes("UTF-8")));
                } else {
                    System.out.println("Found string: " + res);
                    //System.exit(0);
                    int w = socketChannel.write(ByteBuffer.wrap("AGAINDONE".concat(res).getBytes("UTF-8")));
                    System.out.println("bytes written: " + w);
                    return;
                }
            }
        }
        return;

    }

    private boolean readAnswer(SocketChannel server) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        server.read(buffer);
        String msg = new String(buffer.array()).trim();

        if ("OK".equals(msg.substring(0, 2))) {
            String[] result = msg.substring(2).split(";");
            currentLength = Integer.parseInt(result[0]);
            if (result.length == 3) {
                md5 = result[2];
            }

            if (result.length > 1) {
                currentPreffix = result[1];
            }

            if (server != null && server.isOpen()) {
                server.close();
            }

            return true;
        }

        if (server != null && server.isOpen()) {
            server.close();
        }

        return false;
    }

    private String checkMD5() throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        Vector<Character> vec = new Vector<>();
        for (char c :currentPreffix.toCharArray()) {
            vec.add(c);
        }
        while (vec.size() < currentLength) {
            vec.add('A');
        }

        while(true) {
            String str = convertVector(vec);
            byte[] tempMD5 = digest.digest(str.getBytes("UTF-8"));

            //System.out.println("Current string: " + str);


            StringBuilder builder = new StringBuilder();

            for (int j = 0; j < tempMD5.length; ++j) {
                String hex = Integer.toHexString(tempMD5[j] & 0xFF);

                if (hex.length() == 1) {
                    builder.append("0");
                }

                builder.append(hex);
            }

            //System.out.println(builder.toString());
            if (md5.equals(builder.toString())) {
                return str;
            }
            //if (digest.isEqual(md5, tempMD5)) {
            //    return str;
            //}

            int pos = Server.getChangePos(vec);
            //System.out.println("pos: " + pos);
            if (pos >= currentPreffix.length() && pos >= 0) {
                //System.out.println("getting next string");
                for (int i = pos; i < vec.size(); ++i) {
                    vec.set(i, Server.getNextLetter(vec.get(i)));
                    //System.out.println(convertVector(vec));
                }
            } else {
                break;
            }
        }

        return null;

    }

    private String convertVector(Vector<Character> vector) throws UnsupportedEncodingException {
        StringBuilder builder = new StringBuilder();
        for (Character c : vector) {
            builder.append(c);
        }
        return builder.toString();
    }


}

public class MainClient {

    public static String usage = "Wrong arguments. Usage: java MainClient <serverIP> <server port>";

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException {
        if (args.length != 2) {
            throw new IllegalArgumentException(usage);
        }

        Client client = new Client(args[0], Integer.parseInt(args[1]));
        client.searchMD5();
        return;

    }


}
