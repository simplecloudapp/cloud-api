package app.simplecloud.api.internal;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A NATS server for tests that speaks just enough of the protocol for one jnats client: it greets it, answers
 * its pings, remembers its subscriptions, and delivers what a test hands it to the matching ones. It drops
 * what the client publishes and checks no credential.
 */
final class FakeNatsServer implements AutoCloseable {

    private static final String INFO = "INFO {\"server_id\":\"fake\",\"version\":\"2.10.0\",\"proto\":1,"
            + "\"headers\":true,\"max_payload\":1048576}\r\n";

    /**
     * One subscription of the client: the subject, without wildcards, and the client's id for it.
     */
    private record Subscription(String subject, String sid) {
    }

    private final ServerSocket serverSocket;
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final List<Socket> clients = new CopyOnWriteArrayList<>();
    private volatile OutputStream toClient;

    FakeNatsServer() throws IOException {
        this.serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread.ofPlatform().daemon(true).name("fake-nats-accept").start(this::acceptClients);
    }

    String url() {
        return "nats://127.0.0.1:" + serverSocket.getLocalPort();
    }

    boolean hasSubscription(String subject) {
        return subscriptions.stream().anyMatch(subscription -> subscription.subject().equals(subject));
    }

    /**
     * Sends the payload to every subscription of the subject.
     */
    void deliver(String subject, byte[] payload) throws IOException {
        OutputStream out = toClient;
        if (out == null) {
            throw new IOException("No client is connected");
        }
        for (Subscription subscription : subscriptions) {
            if (subscription.subject().equals(subject)) {
                String header = "MSG " + subject + " " + subscription.sid() + " " + payload.length + "\r\n";
                writeMessage(out, header, payload);
            }
        }
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
        for (Socket client : clients) {
            client.close();
        }
    }

    private void acceptClients() {
        while (!serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                clients.add(client);
                Thread.ofPlatform().daemon(true).name("fake-nats-client").start(() -> serve(client));
            } catch (IOException closed) {
                return;
            }
        }
    }

    private void serve(Socket client) {
        try (client) {
            OutputStream out = client.getOutputStream();
            InputStream in = new BufferedInputStream(client.getInputStream());
            subscriptions.clear();
            toClient = out;
            write(out, INFO);
            String line = readLine(in);
            while (line != null) {
                answer(line, in, out);
                line = readLine(in);
            }
        } catch (IOException disconnected) {
            // The client or the test closed the connection.
        }
    }

    private void answer(String line, InputStream in, OutputStream out) throws IOException {
        String[] parts = line.split(" ");
        String operation = parts[0].toUpperCase();
        switch (operation) {
            case "PING" -> write(out, "PONG\r\n");
            case "SUB" -> subscriptions.add(new Subscription(parts[1], parts[parts.length - 1]));
            case "PUB", "HPUB" -> skipPayload(in, Integer.parseInt(parts[parts.length - 1]));
            default -> {
            }
        }
    }

    private static void skipPayload(InputStream in, int size) throws IOException {
        long payloadAndLineEnd = size + 2L;
        in.skipNBytes(payloadAndLineEnd);
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int next = in.read();
        while (next != -1 && next != '\n') {
            if (next != '\r') {
                line.write(next);
            }
            next = in.read();
        }
        boolean ended = next == -1 && line.size() == 0;
        return ended ? null : line.toString(StandardCharsets.UTF_8);
    }

    private static void write(OutputStream out, String text) throws IOException {
        synchronized (out) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    private static void writeMessage(OutputStream out, String header, byte[] payload) throws IOException {
        synchronized (out) {
            out.write(header.getBytes(StandardCharsets.UTF_8));
            out.write(payload);
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }
}
