package io.github.miklires.mauth.email;

import io.github.miklires.mauth.config.ConfigManager;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class SmtpClient {

    private final ConfigManager cfg;

    public SmtpClient(ConfigManager cfg) {
        this.cfg = cfg;
    }

    public void send(String recipient, String subject, String body) throws IOException {
        checkHeader(recipient);
        String host = cfg.getEmailHost();
        int port = cfg.getEmailPort();
        Socket socket = cfg.isEmailSsl()
                ? SSLSocketFactory.getDefault().createSocket()
                : new Socket();
        socket.connect(new InetSocketAddress(host, port), cfg.getEmailTimeoutMillis());
        socket.setSoTimeout(cfg.getEmailTimeoutMillis());
        if (socket instanceof SSLSocket ssl) ssl.startHandshake();

        try (socket) {
            Connection c = new Connection(socket);
            c.expect(220);
            c.command("EHLO " + cfg.getEmailEhlo(), 250);
            if (cfg.isEmailStartTls()) {
                c.command("STARTTLS", 220);
                SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                SSLSocket tls = (SSLSocket) factory.createSocket(socket, host, port, true);
                tls.startHandshake();
                c = new Connection(tls);
                c.command("EHLO " + cfg.getEmailEhlo(), 250);
            }
            if (!cfg.getEmailUsername().isBlank()) {
                String auth = "\0" + cfg.getEmailUsername() + "\0" + cfg.getEmailPassword();
                c.command("AUTH PLAIN " + Base64.getEncoder().encodeToString(
                        auth.getBytes(StandardCharsets.UTF_8)), 235);
            }
            c.command("MAIL FROM:<" + cfg.getEmailFromAddress() + ">", 250);
            c.command("RCPT TO:<" + recipient + ">", 250, 251);
            c.command("DATA", 354);
            c.write(message(recipient, subject, body));
            c.write("\r\n.\r\n");
            c.expect(250);
            c.command("QUIT", 221);
        }
    }

    private String message(String recipient, String subject, String body) {
        checkHeader(subject);
        checkHeader(cfg.getEmailFromAddress());
        checkHeader(cfg.getEmailFromName());
        String encodedSubject = "=?UTF-8?B?" + Base64.getEncoder().encodeToString(
                subject.getBytes(StandardCharsets.UTF_8)) + "?=";
        String encodedName = "=?UTF-8?B?" + Base64.getEncoder().encodeToString(
                cfg.getEmailFromName().getBytes(StandardCharsets.UTF_8)) + "?=";
        String encodedBody = Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(body.getBytes(StandardCharsets.UTF_8));
        return "From: " + encodedName + " <" + cfg.getEmailFromAddress() + ">\r\n"
                + "To: <" + recipient + ">\r\n"
                + "Subject: " + encodedSubject + "\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: base64\r\n\r\n"
                + encodedBody;
    }

    private void checkHeader(String value) {
        if (value.contains("\r") || value.contains("\n")) {
            throw new IllegalArgumentException("invalid email header");
        }
    }

    private static class Connection {
        private final BufferedReader in;
        private final BufferedWriter out;

        Connection(Socket socket) throws IOException {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
        }

        void command(String value, int... expected) throws IOException {
            write(value + "\r\n");
            expect(expected);
        }

        void write(String value) throws IOException {
            out.write(value);
            out.flush();
        }

        void expect(int... expected) throws IOException {
            String line;
            int code = -1;
            do {
                line = in.readLine();
                if (line == null || line.length() < 3) throw new IOException("smtp connection closed");
                try {
                    code = Integer.parseInt(line.substring(0, 3));
                } catch (NumberFormatException e) {
                    throw new IOException("invalid smtp response", e);
                }
            } while (line.length() > 3 && line.charAt(3) == '-');
            for (int value : expected) if (code == value) return;
            throw new IOException("smtp returned " + code);
        }
    }
}
