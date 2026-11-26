package com.mycompany.routeph1;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Properties;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** Sends email through Gmail SMTP (SSL, port 465) with no external library. */
public class Mailer {

    static String user, appPass;

    public static void init(Properties cfg) {
        user = cfg.getProperty("mail.user", "").trim();
        appPass = cfg.getProperty("mail.appPassword", "").replace(" ", "").trim();
    }

    public static boolean isConfigured() {
        return !user.isEmpty() && !user.startsWith("yourgmail") && !appPass.isEmpty() && !appPass.startsWith("xxxx");
    }

    public static void send(String to, String subject, String html) throws IOException {
        try (SSLSocket s = (SSLSocket) SSLSocketFactory.getDefault().createSocket("smtp.gmail.com", 465)) {
            s.setSoTimeout(20000);
            BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            expect(r, 220);
            cmd(w, r, "EHLO localhost", 250);
            cmd(w, r, "AUTH LOGIN", 334);
            cmd(w, r, b64(user), 334);
            cmd(w, r, b64(appPass), 235);
            cmd(w, r, "MAIL FROM:<" + user + ">", 250);
            cmd(w, r, "RCPT TO:<" + to + ">", 250);
            cmd(w, r, "DATA", 354);

            StringBuilder m = new StringBuilder();
            m.append("From: Commuter Route Guide <").append(user).append(">\r\n");
            m.append("To: <").append(to).append(">\r\n");
            m.append("Subject: =?UTF-8?B?").append(b64(subject)).append("?=\r\n");
            m.append("Date: ").append(ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME)).append("\r\n");
            m.append("MIME-Version: 1.0\r\n");
            m.append("Content-Type: text/html; charset=UTF-8\r\n");
            m.append("Content-Transfer-Encoding: base64\r\n\r\n");
            m.append(Base64.getMimeEncoder(76, "\r\n".getBytes()).encodeToString(html.getBytes(StandardCharsets.UTF_8)));
            m.append("\r\n.\r\n");
            w.write(m.toString());
            w.flush();
            expect(r, 250);
            cmd(w, r, "QUIT", 221);
        }
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static void cmd(BufferedWriter w, BufferedReader r, String line, int code) throws IOException {
        w.write(line + "\r\n");
        w.flush();
        expect(r, code);
    }

    private static void expect(BufferedReader r, int code) throws IOException {
        String line;
        do {
            line = r.readLine();
            if (line == null) throw new IOException("SMTP connection closed");
        } while (line.length() > 3 && line.charAt(3) == '-');
        if (!line.startsWith(String.valueOf(code))) throw new IOException("SMTP error: " + line);
    }
}
