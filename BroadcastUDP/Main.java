//package ru.nsu.ccfit.kazantseva;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.HashSet;

class ShutDownHook extends Thread {
    private SocketAddress broadcastAddress;
    private DatagramChannel channel;

    public ShutDownHook(SocketAddress broadcastAddress, DatagramChannel channel) {
        this.broadcastAddress = broadcastAddress;
        this.channel = channel;
    }

    public void run() {
        String exit = "IEXIT";
        try {
            channel.send(ByteBuffer.wrap(exit.getBytes()), broadcastAddress);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
}

public class Main {
    public static void main(String[] args) throws IOException {

        String born = "IBORN";
        String live = "ILIVE";
        String exit = "IEXIT";

        HashSet<SocketAddress> copies = new HashSet<>();

        DatagramChannel channel = DatagramChannel.open();
        DatagramSocket socket = channel.socket();
        socket.bind(new InetSocketAddress(8888));
        socket.setBroadcast(true);

        SocketAddress broadcastAddress = new InetSocketAddress("255.255.255.255", 8888);

        Runtime.getRuntime().addShutdownHook(new ShutDownHook(broadcastAddress, channel));

        ByteBuffer buffer = ByteBuffer.wrap(born.getBytes());

        channel.send(buffer, broadcastAddress);
        SocketAddress sAddress;

        while(true) {
            sAddress = channel.receive(buffer);

            String message = new String(buffer.array());

            if (born.equals(message)) {
                copies.add(sAddress);
                System.out.printf("New instance available: %s. Total: %d\n", sAddress.toString(), copies.size());
                channel.send(ByteBuffer.wrap(live.getBytes()), broadcastAddress);
                buffer.clear();
                continue;
            }

            if (live.equals(message)) {
                if (!copies.contains(sAddress)) {
                    copies.add(sAddress);
                    System.out.printf("New instance discovered: %s. Total: %d\n", sAddress.toString(), copies.size());
                }
                buffer.clear();
                continue;
            }

            if (exit.equals(message)) {
                copies.remove(sAddress);
                System.out.printf("One instance went down: %s. Total: %d\n", sAddress.toString(), copies.size());
                buffer.clear();
            }
        }
    }
}

