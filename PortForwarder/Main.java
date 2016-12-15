import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

class ConnectionInfo {
    private SocketChannel sChannel;
    private SocketChannel rChannel;
    private ByteBuffer bufferOne;
    private ByteBuffer bufferTwo;
    private int shutdownState;

    ConnectionInfo(SocketChannel sChannel, SocketChannel rChannel) {
        this.sChannel = sChannel;
        this.rChannel = rChannel;
        bufferOne = ByteBuffer.allocate(1024*1024);
        bufferTwo = ByteBuffer.allocate(1024*1024);
        this.shutdownState = 0;
    }

    ByteBuffer getWriteBuffer(SocketChannel channel) {
        return channel.equals(sChannel) ? bufferOne : bufferTwo;
    }

    ByteBuffer getReadBuffer(SocketChannel channel) {
       return channel.equals(rChannel) ? bufferOne : bufferTwo;
    }

    SocketChannel getChannel(SocketChannel channel) {
        return channel.equals(sChannel) ? rChannel : sChannel;
    }

    int getShutdownState() {
        return this.shutdownState;
    }

    void setShutdownState() {
        ++this.shutdownState;
    }
}

public class Main {
    public static void main(String[] args) throws IOException {

        if (args.length != 3) {
            throw new IllegalArgumentException("Wrong arguments. Usage: java Main <lport> <rhost> <rport>");
        }

        SocketAddress rAddress = new InetSocketAddress(args[1], Integer.parseInt(args[2]));

        ServerSocketChannel channel = ServerSocketChannel.open();
        channel.bind(new InetSocketAddress(Integer.parseInt(args[0])));
        channel.configureBlocking(false);

        Selector selector = Selector.open();
        channel.register(selector, channel.validOps());

        while (true) {
            int readyChannels = selector.select(1000);
            if (readyChannels == 0) {
                continue;
            }

            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();

                try {
                    if (key.isAcceptable()) {
                        acceptKey(channel, selector, rAddress);
                    } else if (key.isConnectable()) {
                        connectKey(key);
                    } else if (key.isReadable()) {
                        readKey(key, selector);
                    } else if (key.isWritable()) {
                        writeKey(key, selector);
                    }
                } catch (CancelledKeyException e) {

                }

                keyIterator.remove();
            }
        }
    }

    private static void acceptKey(ServerSocketChannel channel, Selector selector, SocketAddress rAddress) throws IOException {
        try {
            SocketChannel clientFrom = channel.accept();
            SocketChannel clientTo = SocketChannel.open();

            clientFrom.configureBlocking(false);
            clientTo.configureBlocking(false);

            ConnectionInfo info = new ConnectionInfo(clientFrom, clientTo);
            clientFrom.register(selector, SelectionKey.OP_READ, info);
            try {
                if (clientTo.connect(rAddress)) {
                    clientTo.register(selector, SelectionKey.OP_READ, info);
                } else {
                    clientTo.register(selector, SelectionKey.OP_CONNECT, info);
                }
            } catch (IOException e) {
                clientFrom.keyFor(selector).cancel();
                clientTo.close();
                clientFrom.close();
            }
        } catch(CancelledKeyException e) {

        }

        //System.out.println("ACCEPT");
    }

    private static void connectKey(SelectionKey key) throws IOException {
        try {
            ((SocketChannel) key.channel()).finishConnect();
            key.interestOps(key.interestOps() & ~SelectionKey.OP_CONNECT | SelectionKey.OP_READ);
        } catch (CancelledKeyException e) {

        }

        //System.out.println("CONNECT");
    }

    private static void readKey(SelectionKey key, Selector selector) throws IOException {
        try {
            ConnectionInfo info = (ConnectionInfo) key.attachment();
            SocketChannel clientFrom = (SocketChannel) key.channel();
            SocketChannel clientTo = info.getChannel(clientFrom);

            ByteBuffer buffer = info.getReadBuffer(clientFrom);
            try {
                if (clientFrom.read(buffer) != -1) {
                    clientTo.keyFor(selector).interestOps(clientTo.keyFor(selector).interestOps() | SelectionKey.OP_WRITE);
                } else {
                    clientTo.shutdownOutput();

                    if (info.getShutdownState() == 1) {
                        key.cancel();
                        clientTo.keyFor(selector).cancel();
                        clientFrom.close();
                        clientTo.close();
                    } else {
                        info.setShutdownState();
                        clientFrom.keyFor(selector).interestOps(clientFrom.keyFor(selector).interestOps() & ~SelectionKey.OP_READ);
                    }
                }
            } catch (IOException e) {
                key.cancel();
                clientTo.keyFor(selector).cancel();
                clientTo.close();
            }
        } catch (CancelledKeyException e) {
            System.out.println("wtf: READ");
        }
        //System.out.println("READ");
    }

    private static void writeKey(SelectionKey key, Selector selector) throws IOException {
        try {
            ConnectionInfo info = (ConnectionInfo) key.attachment();
            SocketChannel clientTo = (SocketChannel) key.channel();
            SocketChannel clientFrom = info.getChannel(clientTo);

            ByteBuffer buffer = info.getWriteBuffer(clientTo);
            buffer.flip();
            try {
                if (clientTo.write(buffer) != -1) {
                    buffer.compact();
                    if (!buffer.hasRemaining()) {
                        clientTo.keyFor(selector).interestOps(clientTo.keyFor(selector).interestOps() & ~SelectionKey.OP_WRITE);
                    }
                } else {
                    clientFrom.shutdownInput();
                    if (info.getShutdownState() == 1) {
                        key.cancel();
                        clientFrom.keyFor(selector).cancel();
                        clientFrom.close();
                        clientTo.close();
                    } else {
                        info.setShutdownState();
                        clientFrom.keyFor(selector).interestOps(clientFrom.keyFor(selector).interestOps() & ~SelectionKey.OP_READ);
                    }
                }
            } catch (IOException e) {
                key.cancel();
                clientFrom.keyFor(selector).cancel();
                clientFrom.close();
            }
        } catch (CancelledKeyException e) {
            System.out.println("wtf: WRITE");
        }

        //System.out.println("WRITE");
    }
}
