package io.helpium.example;

import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Dns;
import okhttp3.OkHttpClient;

/**
 * Lets the emulator reach a Helpium running on the Mac (Herd): the local host
 * name resolves to the Mac (10.0.2.2) and its development certificate
 * authority is trusted. Only for trying the SDK locally; real apps don't need it.
 */
final class LocalServer {
    private LocalServer() {}

    static OkHttpClient client(String host, String caBase64) throws Exception {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        if (!host.isEmpty()) {
            builder.dns(hostname -> hostname.equals(host)
                    ? Collections.singletonList(InetAddress.getByName("10.0.2.2"))
                    : Dns.SYSTEM.lookup(hostname));
        }

        if (!caBase64.isEmpty()) {
            X509TrustManager trust = trustingAlso(caBase64);
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, new TrustManager[]{trust}, null);
            builder.sslSocketFactory(ssl.getSocketFactory(), trust);
        }

        return builder.build();
    }

    /** The system's trusted authorities plus the local one. */
    private static X509TrustManager trustingAlso(String caBase64) throws Exception {
        X509Certificate ca = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(Base64.decode(caBase64, Base64.DEFAULT)));

        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, null);
        store.setCertificateEntry("local", ca);
        X509TrustManager local = trustManager(store);
        X509TrustManager system = trustManager(null);

        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                system.checkClientTrusted(chain, authType);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                try {
                    local.checkServerTrusted(chain, authType);
                } catch (CertificateException notLocal) {
                    system.checkServerTrusted(chain, authType);
                }
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return system.getAcceptedIssuers();
            }
        };
    }

    private static X509TrustManager trustManager(KeyStore store) throws Exception {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        return (X509TrustManager) factory.getTrustManagers()[0];
    }
}
