//package ru.nsu.ccfit.kazantseva;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

class ClientInfo {
    private int seconds;
    private double currBytes;
    private double avgSpeed;
    private double lastSecondSpeed;
    private long timeLastCheck;

    ClientInfo() {
        this.seconds = 0;
        this.currBytes = 0;
        this.avgSpeed = 0;
        this.lastSecondSpeed = 0;
        this.timeLastCheck = System.currentTimeMillis();
    }

    double getAvgSpeed(long currTime) {
        avgSpeed += lastSecondSpeed;
        if (seconds != 1) {
            avgSpeed /= 2;
        }
        currBytes = 0;
        return avgSpeed;
    }

    double getLastSecondSpeed(long currTime) {
        if (seconds++ == 0) {
            lastSecondSpeed = currBytes / ((double)(currTime - timeLastCheck) / 1000);
        } else {
            lastSecondSpeed = currBytes;
        }

        return lastSecondSpeed;
    }

    void addBytes(long bytes) {
        this.currBytes += bytes;
    }

}

public class Server {
    public static void main(String[] args) throws IOException {

        HashMap<SocketAddress, ClientInfo> clients = new HashMap<>();

        ServerSocketChannel channel = ServerSocketChannel.open();
        channel.bind(new InetSocketAddress(8888));
        channel.configureBlocking(false);

        Selector selector = Selector.open();
        channel.register(selector, channel.validOps());

        long timeCheck = System.currentTimeMillis() + 1000;
        while(true) {

            while (timeCheck >= System.currentTimeMillis()) {
                int readyChannels = selector.selectNow();
                if (readyChannels == 0) {
                    continue;
                }

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();

                    if (key.isAcceptable()) {

                        SocketChannel client = channel.accept();
                        client.configureBlocking(false);
                        client.register(selector, SelectionKey.OP_READ);

                        clients.put(client.getRemoteAddress(), new ClientInfo());
                        System.out.printf("Accepted new connection from client: %s%n", client.getRemoteAddress());

                    } else if (key.isReadable()) {

                        SocketChannel client = (SocketChannel) key.channel();
                        ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);

                        long bytes = client.read(buffer);
                        clients.get(client.getRemoteAddress()).addBytes(bytes);

                    }

                    keyIterator.remove();
                }
            }

            System.out.println("Current speed:");
            for (SocketAddress client : clients.keySet()) {
                double lastSecondSpeed = clients.get(client).getLastSecondSpeed(timeCheck) / (1024*1024);
                double speedMBytes = clients.get(client).getAvgSpeed(timeCheck) / (1024*1024);
                System.out.printf("%s: %dMb/s, %dMb/s%n", client, (int)lastSecondSpeed, (int)speedMBytes);
            }

            timeCheck = System.currentTimeMillis() + 1000;
        }

    }
}
