//package ru.ccfit.nsu.kazantseva;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class Client {
    public static void main(String[] args) throws IOException {
        //check command line arguments
        if (args.length < 2) {
            throw new IllegalArgumentException("Wrong arguments required. Example: java Client <server IP> <file path>");
        }

        //try to open a channel for reading from file
        StringBuilder nameBuilder = new StringBuilder(args[1]);
        int j = 2;
        while(j < args.length) {
            nameBuilder.append(" ");
            nameBuilder.append(args[j++]);
        }

        Path filePath = Paths.get(nameBuilder.toString());
        FileChannel fileChannel = FileChannel.open(filePath, StandardOpenOption.READ);

        //get name and size of file
        long size = fileChannel.size();
        StringBuilder fileInfo = new StringBuilder(filePath.getFileName().toString());
        fileInfo.append(";");
        fileInfo.append(size);

        //try to connect to server
        String serverIP = args[0];
        SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress(serverIP, 8888));

        //write name and size of file to the channel
        ByteBuffer buffer = ByteBuffer.wrap(fileInfo.toString().getBytes("UTF-8"));
        socketChannel.write(buffer);

        //send file, 1Kb at once
        long bytesSent = 0;
        while (bytesSent < size) {
            bytesSent += fileChannel.transferTo(bytesSent, 1024, socketChannel);
        }

        fileChannel.close();
        socketChannel.close();

        System.out.println("File transferred");
    }




}
