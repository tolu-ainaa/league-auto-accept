package autoaccept;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Talks to the local League Client (LCU) API on https://127.0.0.1:port.
 * The client authenticates with basic auth (user "riot", password from the
 * client's own launch arguments) and serves a self-signed certificate.
 */
public final class LcuClient {

    static {
        // The LCU cert is self-signed for 127.0.0.1; the JDK HttpClient offers
        // no per-request opt-out of hostname verification, only this property.
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
    }

    private final int port;
    private final String authHeader;
    private final HttpClient http;

    private LcuClient(int port, String token) throws Exception {
        this.port = port;
        this.authHeader = "Basic " + Base64.getEncoder()
                .encodeToString(("riot:" + token).getBytes(StandardCharsets.UTF_8));

        TrustManager[] trustAll = { new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        }};
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, trustAll, new SecureRandom());

        this.http = HttpClient.newBuilder()
                .sslContext(ssl)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    /**
     * Finds a running League client and returns a connected LcuClient,
     * or null if the client isn't running (or its credentials can't be read).
     */
    public static LcuClient discover() {
        try {
            String cmd = leagueClientCommandLine();
            if (cmd != null) {
                Matcher port = Pattern.compile("--app-port=(\\d+)").matcher(cmd);
                Matcher token = Pattern.compile("--remoting-auth-token=([\\w-]+)").matcher(cmd);
                if (port.find() && token.find()) {
                    return new LcuClient(Integer.parseInt(port.group(1)), token.group(1));
                }
            }

            // Fallback: lockfile in the default install location.
            // Format: processName:pid:port:password:protocol
            Path lockfile = Path.of("C:\\Riot Games\\League of Legends\\lockfile");
            if (Files.exists(lockfile)) {
                String[] parts = Files.readString(lockfile).trim().split(":");
                if (parts.length >= 5) {
                    return new LcuClient(Integer.parseInt(parts[2]), parts[3]);
                }
            }
        } catch (Exception ignored) {
            // Treat any discovery failure as "client not available yet".
        }
        return null;
    }

    /**
     * Reads the LeagueClientUx.exe command line, which carries the API port
     * and auth token. Java's ProcessHandle can't see other processes' args on
     * Windows, so this shells out to PowerShell/CIM instead.
     */
    private static String leagueClientCommandLine() throws IOException, InterruptedException {
        Process p = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                "(Get-CimInstance Win32_Process -Filter \"Name='LeagueClientUx.exe'\").CommandLine")
                .redirectErrorStream(true)
                .start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor();
        return out.isBlank() ? null : out;
    }

    /** GET a path; returns the response body on 200, or null on any failure. */
    public String get(String path) {
        try {
            HttpResponse<String> r = http.send(request(path).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return r.statusCode() == 200 ? r.body() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** POST (no body) to a path; returns true on any 2xx response. */
    public boolean post(String path) {
        try {
            HttpResponse<String> r = http.send(
                    request(path).POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            return r.statusCode() / 100 == 2;
        } catch (Exception e) {
            return false;
        }
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("https://127.0.0.1:" + port + path))
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(3));
    }
}
