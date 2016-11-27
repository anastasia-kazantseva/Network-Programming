//package ru.ccfit.nsu.kazantseva;

import javafx.util.Pair;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

public class Server {
    public static void main(String[] args) throws IOException {
        HashMap<SocketAddress, Pair<String, Long>> filesInfo = new HashMap<>();
        HashMap<String, FileChannel> files = new HashMap<>();

        ServerSocketChannel channel = ServerSocketChannel.open();
        channel.bind(new InetSocketAddress(8888));
        channel.configureBlocking(false);

        Selector selector = Selector.open();
        channel.register(selector, channel.validOps());

        while (true) {
            int readyChannels = selector.selectNow();
            if (readyChannels == 0) {
                continue;
            }

            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();

                if (key.isAcceptable()) {
                    //accept connection from a new client
                    SocketChannel client = channel.accept();

                    //get name and size of file
                    ByteBuffer buffer = ByteBuffer.allocate(1024);
                    int readBytes = client.read(buffer);

                    //add information about file to hashmap
                    String[] fileState;
                    fileState = new String(buffer.array(), 0, readBytes, "UTF-8").split(";");

                    StringBuilder filePathBuilder = new StringBuilder("ServerFiles\\");
                    filePathBuilder.append(fileState[0]);

                    Path filePath = Paths.get(filePathBuilder.toString());
                    int i = 1;
                    while(true) {
                        try {
                            FileChannel fileChannel = FileChannel.open(filePath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                            //fileChannel.position(0);
                            files.put(filePath.getFileName().toString(), fileChannel);
                            break;
                        } catch (FileAlreadyExistsException e) {
                            //change file name if it already exists
                            int periodIndex = filePathBuilder.indexOf(".");
                            int openIndex = filePathBuilder.indexOf("(");

                            if (periodIndex == -1) {
                                periodIndex = filePathBuilder.length();
                                if (openIndex == -1) {
                                    openIndex = filePathBuilder.length();
                                }

                            } else {
                                if (openIndex == -1) {
                                    openIndex = periodIndex;
                                }
                            }

                            filePathBuilder.replace(openIndex, periodIndex, String.format("(%d)", i));
                            filePath = Paths.get(filePathBuilder.toString());
                            ++i;
                        }
                    }

                    filesInfo.put(client.getRemoteAddress(), new Pair<>(filePath.getFileName().toString(), Long.parseLong(fileState[1])));

                    //add channel to selector
                    client.configureBlocking(false);
                    client.register(selector, SelectionKey.OP_READ);

                } else if (key.isReadable()) {

                    //get information about file associated with this key
                    SocketChannel client = (SocketChannel) key.channel();
                    String fileName = filesInfo.get(client.getRemoteAddress()).getKey();
                    long fileSize = filesInfo.get(client.getRemoteAddress()).getValue();

                    FileChannel fileChannel = files.get(fileName);

                    long position = fileChannel.position();

                    //receive 1Kb
                    long bytesReceived = fileChannel.transferFrom(client, position, 1024);
                    fileChannel.position(position + bytesReceived);

                    //check if file transferred and close connection
                    if (fileChannel.size() >= fileSize) {
                        System.out.printf("File %s transferred%n", fileName);
                        filesInfo.remove(client.getRemoteAddress());
                        files.remove(fileName);

                        key.cancel();
                        client.close();
                        fileChannel.close();
                    }
                }

                keyIterator.remove();
            }
        }

    }

}
