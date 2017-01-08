import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.UUID;

class Client {
    private InetSocketAddress serverAddress;
    private UUID uuid;

    Client(String serverIP, int serverPort) {
        this.serverAddress = new InetSocketAddress(serverIP, serverPort);
        this.uuid = UUID.randomUUID();
    }

    public void searchMD5() throws IOException {
        SocketChannel socketChannel = SocketChannel.open(serverAddress);
        socketChannel.write(ByteBuffer.wrap("FIRST".concat(uuid.toString()).getBytes("UTF-8")));

    }
}

public class MainClient {

    public static String usage = "Wrong arguments. Usage: java MainClient <serverIP> <server port>";

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException(usage);
        }

        Client client = new Client(args[0], Integer.parseInt(args[1]));
        client.searchMD5();
    }


}
