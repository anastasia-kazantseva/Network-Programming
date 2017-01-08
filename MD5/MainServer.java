import javafx.util.Pair;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;

class Server {
    private int port;
    private String md5;
    private int maxLength;

    private ServerSocketChannel channel;
    private Selector selector;

    private HashMap<String, Pair<Long, byte[]>> tasks;

    private boolean available;
    private Vector<Character> lastPreffix;
    private int lastLength;

    Server(int port, String md5, int maxLength) {
        this.port = port;
        this.md5 = md5;
        this.maxLength = maxLength;
        this.tasks = new HashMap<>();
        this.available = true;
        this.lastLength = 0;
        try {
            channel = ServerSocketChannel.open();
            channel.bind(new InetSocketAddress(port));
            channel.configureBlocking(false);
            selector = Selector.open();
            channel.register(selector, channel.validOps());
        } catch (IOException e) {
            throw new NoSuchElementException(e.getLocalizedMessage());
        }
    }

    void searchMD5() throws IOException {
        while(true) {
            if (selector.select(1000) == 0) {
                long time = System.currentTimeMillis();
                boolean flag = false;

                for (String uuid : tasks.keySet()) {
                    if (time - tasks.get(uuid).getKey() < 10000) {
                        flag = true;
                        break;
                    }
                }

                if (flag) {
                    continue;
                } else {
                    break;
                }
            }

            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

            while(keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                if (key.isAcceptable()) {
                    acceptKey();
                } else if (key.isReadable()) {
                    if (readKey(key) != null) {
                        break;
                    }
                }
            }
        }
    }

    private void acceptKey() throws IOException {
        SocketChannel client = channel.accept();
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ);
    }

    private String readKey(SelectionKey key) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        SocketChannel client = (SocketChannel) key.channel();

        client.read(buffer);
        String msg = new String(buffer.array()).trim();

        if ("FIRST".equals(msg.substring(0, 5))) {
            readFirst(msg.substring(5), key);
        } else if ("AGAIN".equals(msg.substring(0, 5))) {
            return readAgain(msg.substring(5), key);
        }

        return null;

    }

    private void readFirst(String uuid, SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();

        if (!available) {
            client.write(ByteBuffer.wrap("NO".getBytes("UTF-8")));
        } else {
            String task = generateNewTask(uuid);
            if (task != null) {
                client.write(ByteBuffer.wrap("OK".concat(task).getBytes("UTF-8")));
            } else {
                client.write(ByteBuffer.wrap("NO".getBytes("UTF-8")));
            }
        }
    }

    private String readAgain(String msg, SelectionKey key) throws IOException {
        if ("MISS".equals(msg.substring(0, 4))) {
            String uuid = msg.substring(4);
            tasks.remove(uuid);
            readFirst(uuid, key);
            return null;
        } else if ("DONE".equals(msg.substring(0, 4))) {
            tasks.clear();
            return msg.substring(4);
        }

        return null;
    }

    private String generateNewTask(String uuid) {
        StringBuilder builder = new StringBuilder();

        Vector<Character> preffix = getNextPreffix();
        if (preffix != null) {
            builder.append(lastLength).append(" ").append(preffix.toString());
            String task = builder.toString();
            tasks.put(uuid, new Pair<Long, byte[]>(System.currentTimeMillis(), task.getBytes()));
            return builder.toString();
        } else {
            String uuidClient = null;
            long time = System.currentTimeMillis();

            for (String addr : tasks.keySet()) {
                Pair<Long, byte[]> elem = tasks.get(addr);
                if (time - elem.getKey() > 10000) {
                    uuidClient = uuid;
                    break;
                }
            }

            if (uuidClient != null) {
                tasks.put(uuidClient, tasks.get(uuid));
                tasks.remove(uuid);
                return new String(tasks.get(uuidClient).getValue());
            }

            if (tasks.isEmpty()) {
                available = false;
            }
            return null;
        }
    }

    public static char getNextLetter(char c) {
        char r = c;
        switch (c) {
            case 'A':
                r = 'C';
                break;
            case 'C':
                r = 'G';
                break;
            case 'G':
                r = 'T';
                break;
            case 'T':
                r = 'A';
        }
        return r;
    }

    private Vector<Character> getNextPreffix() {
        int pos = getChangePos();
        if (pos == -1) {
            ++lastLength;
            if (lastLength >= maxLength) {
                return null;
            }
            for (int i = 0; i < lastPreffix.size(); ++i) {
                lastPreffix.set(i, 'A');
            }
            if (lastPreffix.size() < (lastLength / 5)) {
                lastPreffix.add('A');
            }
        } else {
            for (int i = pos; i < lastPreffix.size(); ++i) {
                lastPreffix.set(i, getNextLetter(lastPreffix.get(i)));
            }
        }
        return lastPreffix;
    }

    private int getChangePos() {
        for (int i = lastPreffix.size() - 1; i >= 0; --i) {
            if (!lastPreffix.get(i).equals('T')) {
                return i;
            }
        }
        return -1;
    }

}

public class MainServer {
    public static String usage = "Wrong arguments. Usage: java MainServer <port> <md5> <max length>";

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            throw new IllegalArgumentException(usage);
        }

        Server server = new Server(Integer.parseInt(args[0]), args[1], Integer.parseInt(args[2]));
        server.searchMD5();


    }

}
