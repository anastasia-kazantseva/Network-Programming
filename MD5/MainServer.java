import javafx.util.Pair;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.NoSuchAlgorithmException;
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
        this.lastPreffix = new Vector<>();
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

    private boolean isEnd() {
        if (lastLength < maxLength/5) {
            return false;
        } else {
            for (char c : lastPreffix) {
                if (c != 'T') {
                    return false;
                }
            }
        }

        if (!tasks.isEmpty()) {
            return false;
        }

        return true;
    }

    void searchMD5() throws IOException {
        while(true) {
            if (selector.select(5000) == 0) {
                if (isEnd()) {
                    System.out.println("Couldn't find string with given md5");
                    return;
                } else {
                    continue;
                }
//                long time = System.currentTimeMillis();
//                boolean flag = false;
//
//                for (String uuid : tasks.keySet()) {
//                    if (time - tasks.get(uuid).getKey() < 30000) {
//                        flag = true;
//                        break;
//                    }
//                }
//
//                if (tasks.isEmpty()) {
//                    flag = true;
//                }
//
//                if (flag) {
//                    continue;
//                } else {
//                    System.out.println("Each client reached timeout");
//                    break;
//                }
            }

            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

            while(keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                if (key.isAcceptable()) {
                    acceptKey();
                    //System.out.println("after accept key");
                } else if (key.isReadable()) {
                    String res = readKey(key);
                    if (res != null) {
                        System.out.println("Result: ".concat(res));
                        return;
                        //break;
                    }
                }
                keyIterator.remove();
            }
        }
    }

    private void acceptKey() throws IOException {
        //System.out.println("in acceptKey");
        SocketChannel client = channel.accept();
        if (client != null) {
            System.out.println("Accepted new connection");
            client.configureBlocking(false);
            client.register(selector, SelectionKey.OP_READ);
        }
    }

    private String readKey(SelectionKey key) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        SocketChannel client = (SocketChannel) key.channel();

        try {
            client.read(buffer);

            String msg = new String(buffer.array()).trim();

            if ("FIRST".equals(msg.substring(0, 5))) {
                System.out.println("New client: " + msg.substring(5));
                readFirst(msg.substring(5), key, true);
            } else if ("AGAIN".equals(msg.substring(0, 5))) {
                System.out.println("Answer from client: " + msg.substring(5));
                key.cancel();
                return readAgain(msg.substring(5), key);
            }
        } catch (IOException e) {

        }

        key.cancel();
        return null;

    }

    private void readFirst(String uuid, SelectionKey key, boolean flag) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();

        if (!available) {
            client.write(ByteBuffer.wrap("NO".getBytes("UTF-8")));
        } else {
            String task = generateNewTask(uuid);
            StringBuilder buildTask = new StringBuilder();
            if (task != null) {
                buildTask.append(task);
                if (flag) {
                    System.out.println("TRUE flag (first connection)");
                    System.out.println("md5" + md5);
                    buildTask.append(";").append(md5);
                    System.out.println("task: " + buildTask);
                }
                System.out.println("First task: " + buildTask);
                client.write(ByteBuffer.wrap("OK".concat(buildTask.toString()).getBytes("UTF-8")));
            } else {
                client.write(ByteBuffer.wrap("NO".getBytes("UTF-8")));
            }
        }
    }

    private String readAgain(String msg, SelectionKey key) throws IOException {
        if ("MISS".equals(msg.substring(0, 4))) {
            String uuid = msg.substring(4);
            tasks.remove(uuid);
            readFirst(uuid, key, false);
            return null;
        } else if ("DONE".equals(msg.substring(0, 4))) {
            tasks.clear();
            return msg.substring(4);
        }

        return null;
    }

    private String generateNewTask(String uuid) throws UnsupportedEncodingException {
        StringBuilder builder = new StringBuilder();

        Vector<Character> preffix = getNextPreffix();
        if (preffix != null) {
            builder.append(lastLength).append(";");
            for (Character c : preffix) {
                builder.append(c);
            }
            String task = builder.toString();
            tasks.put(uuid, new Pair<Long, byte[]>(System.currentTimeMillis(), task.getBytes("UTF-8")));
            return task;
        } else {
            String uuidClient = null;
            long time = System.currentTimeMillis();

            for (String addr : tasks.keySet()) {
                Pair<Long, byte[]> elem = tasks.get(addr);
                if (time - elem.getKey() > 20000) {
                    uuidClient = addr;//uuid
                    break;
                }
            }

            if (uuidClient != null) {

                tasks.put(uuid, tasks.get(uuidClient));
                tasks.remove(uuidClient);
                return new String(tasks.get(uuid).getValue());
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
        int pos = getChangePos(lastPreffix);
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

    public static int getChangePos(Vector<Character> vector) {
        //if (vector.isEmpty()) {
        //    return 0;
        //}
        for (int i = vector.size() - 1; i >= 0; --i) {
            if (!vector.get(i).equals('T')) {
                return i;
            }
        }
        return -1;
    }

}

public class MainServer {
    public static String usage = "Wrong arguments. Usage: java MainServer <port> <md5> <max length>";

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException {
        if (args.length != 3) {
            throw new IllegalArgumentException(usage);
        }

        Server server = new Server(Integer.parseInt(args[0]), args[1], Integer.parseInt(args[2]));
        server.searchMD5();
        return;


    }

}
