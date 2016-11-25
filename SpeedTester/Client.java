//package ru.nsu.ccfit.kazantseva;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class Client {

    public static void main(String[] args) throws IOException {
        String ipAddress = args[0];

        SocketChannel channel = SocketChannel.open(new InetSocketAddress(ipAddress, 8888));

        ByteBuffer buffer = ByteBuffer.allocate(64*1024);

        while(true) {
            channel.write(buffer);
            buffer.clear();
        }

    }
}
