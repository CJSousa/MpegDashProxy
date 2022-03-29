package http;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.URL;

/**
 * Implements a basic HTTP1.0 client
 * @author smduarte
 *
 */
public class HttpClient10 implements HttpClient {

    private static final String HTTP_SUCCESS = "20";
    private static final String GET_FORMAT_STR = "GET %s HTTP/1.0\r\n%s\r\n\r\n";
    private static final String GET_FORMAT_FULL_RANGE = "GET %s HTTP/1.0\r\nRange:bytes=%d-%d\r\n\r\n";
    private static final String GET_FORMAT_START_RANGE = "GET %s HTTP/1.0\r\nRange:bytes=%d-\r\n\r\n";
    private static final String CONTENT_LENGTH = "Content-Length";
    private static final long UNDEFINED_RANGE_VALUE = -1;

    /**
     * Gets a resource's contents.
     * 
     * @param in - The input stream
     * @return the contents of the resource
     * @throws IOException - Thrown to indicate that an I/O exception of some sort has occurred
     */
    static private byte[] getContents(InputStream in) throws IOException {

        String reply = Http.readLine(in);
        if (!reply.contains(HTTP_SUCCESS)) {
            throw new RuntimeException(String.format("HTTP request failed: [%s]", reply));
        }
        while ((reply = Http.readLine(in)).length() > 0) {}
        return in.readAllBytes();
    }
    
    @Override
    public byte[] doGet(String urlStr) {
        try {
            URL url = new URL(urlStr);
            int port = url.getPort();
            try (Socket cs = new Socket(url.getHost(), port < 0 ? url.getDefaultPort(): port)) {
                String request = String.format(GET_FORMAT_STR, url.getFile(), USER_AGENT);
                cs.getOutputStream().write(request.getBytes());
                return getContents(cs.getInputStream());
            }
        } catch (Exception x) {
            x.printStackTrace();
            return null;
        }
    }

    @Override
    public byte[] doGetRange(String urlStr, long start, long end) {
        return getContentsByRange(urlStr, start, end);
    }

    @Override
    public byte[] doGetRange(String url, long start) {
        return getContentsByRange(url, start, UNDEFINED_RANGE_VALUE);
    }
    
    /* Auxiliary Method */
    
    /**
     * Gets a range of a resource's contents, according to 
     * provided offsets, in case they are specified.
     * 
     * @param url   - the URL of the requested resource
     * @param start - the start offset of the requested range
     * @param end   - the end offset of the requested range (inclusive)
     * @return the range of contents the resource, or null if an error occurred
     */
    private  byte[] getContentsByRange(String url, long start, long end) {
        try {
            URL u = new URL(url);            
            int port = u.getPort();
            
            /* Choosing type of request */
            byte[] request;
            if(end == UNDEFINED_RANGE_VALUE) {
                if(start == UNDEFINED_RANGE_VALUE) 
                    request = String.format(GET_FORMAT_STR, u.getPath()).getBytes();
                else request = String.format(GET_FORMAT_START_RANGE, u.getPath(), start).getBytes();
            } else request = String.format(GET_FORMAT_FULL_RANGE, u.getPath(), start, end).getBytes();
            
            /* Processing the request */
            try (Socket cs = new Socket(u.getHost(), port > 0 ? port : HTTP_DEFAULT_PORT)) {
                cs.getOutputStream().write(request);
                InputStream in = cs.getInputStream();
                String statusLine = Http.readLine(in);
                String[] statusParts = Http.parseHttpReply(statusLine);

                if (statusParts[1].contains(HTTP_SUCCESS)) {
                    String headerLine;
                    int contentLength = -1;
                    while ((headerLine = Http.readLine(in)).length() > 0) {
                        String[] headerParts = Http.parseHttpHeader(headerLine);
                        if (headerParts[0].equalsIgnoreCase(CONTENT_LENGTH))
                            contentLength = Integer.valueOf(headerParts[1]);
                    }
                    /* Reading payload if it exists */
                    if (contentLength >= 0)
                        return in.readNBytes(contentLength);
                    else
                        return in.readAllBytes();
                    
                } else throw new RuntimeException(String.format("HTTP request failed: [%s]", statusParts[1]));
            }
        } catch (Exception x) {
            x.printStackTrace();
        }
        return null;
    }
    
    
}