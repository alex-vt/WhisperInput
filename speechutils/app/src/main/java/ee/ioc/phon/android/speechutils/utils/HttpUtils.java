package ee.ioc.phon.android.speechutils.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

import ee.ioc.phon.android.speechutils.Log;

public class HttpUtils {

    // Timeouts in milliseconds
    // TODO: make settable
    private static final int DEFAULT_READ_TIMEOUT = 3000;
    private static final int DEFAULT_CONNECT_TIMEOUT = 4000;

    private HttpUtils() {
    }

    public static String getUrl(String url) throws IOException {
        return fetchUrl(url, "GET", null);
    }

    public static String fetchUrl(String myurl, String method, String body) throws IOException {
        return fetchUrl(myurl, method, body, null);
    }

    public static String fetchUrl(String myurl, String method, String body, Map<String, String> properties)
            throws IOException {
        byte[] outputInBytes = null;

        if (body != null) {
            outputInBytes = body.getBytes("UTF-8");
        }

        InputStream is = null;

        try {
            URL url = new URL(myurl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(DEFAULT_READ_TIMEOUT);
            conn.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT);
            conn.setRequestMethod(method);
            if (properties != null) {
                for (Map.Entry<String, String> entry : properties.entrySet()) {
                    String value = entry.getValue();
                    if (value != null) {
                        conn.setRequestProperty(entry.getKey(), value);
                    }
                }
            }
            conn.setDoInput(true);

            if (outputInBytes == null) {
                conn.connect();
            } else {
                conn.setDoOutput(true);
                OutputStream os = conn.getOutputStream();
                os.write(outputInBytes);
                os.close();
            }

            // TODO: improve handling of response code
            //int response = conn.getResponseCode();
            is = conn.getInputStream();
            return inputStreamToString(is, 1024);
        } finally {
            closeQuietly(is);
        }
    }

    public static String encode(String text) {
        try {
            return URLEncoder.encode(text, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // This should never happen
            return "";
        }
    }

    private static void closeQuietly(InputStream is) {
        try {
            if (is != null) {
                is.close();
            }
        } catch (Exception e) {
            Log.i(e.getMessage());
        }
    }

    private static String inputStreamToString(final InputStream is, final int bufferSize) throws IOException {
        final char[] buffer = new char[bufferSize];
        final StringBuilder out = new StringBuilder();
        Reader in = new InputStreamReader(is, "UTF-8");
        while (true) {
            int rsz = in.read(buffer, 0, buffer.length);
            if (rsz < 0) {
                break;
            }
            out.append(buffer, 0, rsz);
        }
        return out.toString();
    }
}
