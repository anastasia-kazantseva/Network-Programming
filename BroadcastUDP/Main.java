//package ru.nsu.ccfit.kazantseva;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.HashMap;
import java.util.Iterator;

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

        HashMap<SocketAddress, Long> copies = new HashMap<>();

        DatagramChannel channel = DatagramChannel.open();
        DatagramSocket socket = channel.socket();
        socket.bind(new InetSocketAddress(8888));
        socket.setBroadcast(true);
        channel.configureBlocking(false);

        SocketAddress broadcastAddress = new InetSocketAddress("255.255.255.255", 8888);

        Runtime.getRuntime().addShutdownHook(new ShutDownHook(broadcastAddress, channel));

        ByteBuffer buffer = ByteBuffer.wrap(born.getBytes());

        channel.send(buffer, broadcastAddress);
        long sendTime = System.currentTimeMillis() + 1000;
        long checkTime = System.currentTimeMillis() + 5000;
        long currTime;

        SocketAddress sAddress;

        while(true) {
            while (System.currentTimeMillis() < sendTime) {
                sAddress = channel.receive(buffer);
                if (sAddress == null) {
                    continue;
                }

                String message = new String(buffer.array());

                if (born.equals(message)) {
                    copies.put(sAddress, System.currentTimeMillis());
                    System.out.printf("New instance available: %s. Total: %d\n", sAddress.toString(), copies.size());
                    channel.send(ByteBuffer.wrap(live.getBytes()), broadcastAddress);
                    buffer.clear();
                    continue;
                }

                if (live.equals(message)) {
                    if (!copies.containsKey(sAddress)) {
                        System.out.printf("New instance discovered: %s. Total: %d\n", sAddress.toString(), copies.size() + 1);
                    }
                    copies.put(sAddress, System.currentTimeMillis());
                    buffer.clear();
                    continue;
                }

                if (exit.equals(message)) {
                    copies.remove(sAddress);
                    System.out.printf("One instance went down: %s. Total: %d\n", sAddress.toString(), copies.size());
                    buffer.clear();
                }
            }

            channel.send(ByteBuffer.wrap(live.getBytes()), broadcastAddress);
            sendTime = System.currentTimeMillis() + 1000;

            if ((currTime = System.currentTimeMillis()) >= checkTime) {
                Iterator iterator = copies.entrySet().iterator();
                while(iterator.hasNext()) {
                    HashMap.Entry pair = (HashMap.Entry)iterator.next();
                    if (currTime - (long)pair.getValue() > 5000 && channel.getLocalAddress() != pair.getKey()) {
                        System.out.printf("One instance went down: %s. Total: %d\n", pair.getKey().toString(), copies.size() - 1);
                        iterator.remove();
                    }

                }
            }

        }
    }
}

