package com.chapchap.customer.global.config.customerai;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.io.IOException;
import java.net.HttpURLConnection;

public final class NoRedirectClientHttpRequestFactory extends SimpleClientHttpRequestFactory {
    @Override
    protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
        super.prepareConnection(connection, method);
        connection.setInstanceFollowRedirects(false);
    }
}
