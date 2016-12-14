import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

class ConnectionInfo {
    private SocketChannel sChannel;
    private SocketChannel rChannel;
    private ByteBuffer bufferOne;
    private ByteBuffer bufferTwo;

    ConnectionInfo(SocketChannel sChannel, SocketChannel rChannel) {
        this.sChannel = sChannel;
        this.rChannel = rChannel;
        bufferOne = ByteBuffer.allocate(1024*1024);
        bufferTwo = ByteBuffer.allocate(1024*1024);
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
            int readyChannels = selector.selectNow();
            if (readyChannels == 0) {
                continue;
            }

            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();

                if (key.isAcceptable()) {
                    acceptKey(channel, selector, rAddress);
                } else if (key.isConnectable()) {
                    connectKey(key);
                } else if (key.isReadable()) {
                    readKey(key, selector);
                } else if (key.isWritable()) {
                    writeKey(key, selector);
                }

                keyIterator.remove();
            }
        }
    }

    private static void acceptKey(ServerSocketChannel channel, Selector selector, SocketAddress rAddress) throws IOException {
        SocketChannel clientFrom = channel.accept();
        SocketChannel clientTo = SocketChannel.open();
        //System.out.println("one");

        clientFrom.configureBlocking(false);
        clientTo.configureBlocking(false);
        //System.out.println("two");
        ConnectionInfo info = new ConnectionInfo(clientFrom, clientTo);
        //System.out.println("three");
        clientFrom.register(selector, SelectionKey.OP_READ, info);
        if (clientTo.connect(rAddress)) {
            //System.out.println("four");
            clientTo.register(selector, SelectionKey.OP_READ, info);
            //System.out.println("five");
        } else {
            //System.out.println("four_2");
            clientTo.register(selector, SelectionKey.OP_CONNECT, info);
            //System.out.println("five_2");
        }

        //clientFrom.keyFor(selector).attach(info);
        //clientTo.keyFor(selector).attach(info);
        System.out.println("ACCEPT");
    }

    private static void connectKey(SelectionKey key) throws IOException {
        ((SocketChannel)key.channel()).finishConnect();
        key.interestOps(key.interestOps() & ~SelectionKey.OP_CONNECT | SelectionKey.OP_READ);
        System.out.println("CONNECT");
    }

    private static void readKey(SelectionKey key, Selector selector) throws IOException {
        ConnectionInfo info = (ConnectionInfo) key.attachment();
        //System.out.println("one");
        SocketChannel clientFrom = (SocketChannel) key.channel();
        //System.out.println("two");
        SocketChannel clientTo = info.getChannel(clientFrom);
        //System.out.println("three");

        clientFrom.read(info.getReadBuffer(clientFrom));
        //System.out.println("four");
        //clientTo.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, info);
        clientTo.keyFor(selector).interestOps(clientTo.keyFor(selector).interestOps() | SelectionKey.OP_WRITE);
        //System.out.println("five");
        System.out.println("READ");
    }

    private static void writeKey(SelectionKey key, Selector selector) throws IOException {
        ConnectionInfo info = (ConnectionInfo) key.attachment();
        SocketChannel clientTo = (SocketChannel) key.channel();
        //SocketChannel clientFrom = info.getChannel(clientTo);

        ByteBuffer buffer = info.getWriteBuffer(clientTo);

        clientTo.write(buffer);
        buffer.compact();

        if (!buffer.hasRemaining()) {
            //clientTo.register(selector, SelectionKey.OP_READ, info);
            clientTo.keyFor(selector).interestOps(clientTo.keyFor(selector).interestOps() & ~SelectionKey.OP_WRITE);
        }
        System.out.println("WRITE");
    }
}
