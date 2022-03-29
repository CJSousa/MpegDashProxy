package http;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;

/**
 * Implements a basic HTTP1.1 client
 * @authors Clara Sousa 58403 - Paula Ines Lopes 58655
 *
 */

public class HttpClient11 implements HttpClient, AutoCloseable{
	
	private static final String HTTP_SUCCESS = "20";
	private static final String GET_FORMAT_STR = "GET %s HTTP/1.1\r\n\r\n";
	private static final String GET_FORMAT_START_RANGE = "GET %s HTTP/1.1\r\nRange:bytes=%d-\r\n\r\n";
	private static final String GET_FORMAT_FULL_RANGE = "GET %s HTTP/1.1\r\nRange:bytes=%d-%d\r\n\r\n";
	private static final String CONTENT_LENGTH = "Content-Length";
	private static final long UNDEFINED_RANGE_VALUE = -1;
	private static final String DIFFERENT_SERVER_EXCEPTION= "Different server is not expected!";
	
	Socket cs;
	URL url;
	int port;

	public HttpClient11 (String urlStr) {
		try {
			url = new URL(urlStr);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		port = url.getPort();
		try {
			cs = new Socket(url.getHost(), port < 0 ? url.getDefaultPort(): port);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public byte[] doGet(String url) {
		return getContents(url, UNDEFINED_RANGE_VALUE, UNDEFINED_RANGE_VALUE);
	}
	@Override
	public byte[] doGetRange(String url, long start) {
		return getContents(url, start, UNDEFINED_RANGE_VALUE);
	}
	@Override
	public byte[] doGetRange(String url, long start, long end){
		try {
			return getContents(url, start, end);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;	
	}
	
	@Override
	public void close() throws IOException {
		try {
			cs.close();
		} catch(Exception e) {
			// Nothing to be done with the exception
		}	
	}
	
	/* Auxiliary Methods */
	
	/**
	 * Gets a range of a resource's contents, according to 
	 * provided offsets, in case they are specified.
	 * 
	 * @param urlStr - the URL of the requested resource
	 * @param start	 - the start offset of the requested range
	 * @param end 	 - the end offset of the requested range (inclusive)
	 * @return the range of contents the resource, or null if an error occurred
	 * @throws MalformedURLException - Thrown to indicate that a malformed URL has occurred
	 * @throws IOException - Thrown to indicate that an I/O exception of some sort has occurred
	 */
	private byte[] getContents(String urlStr, long start, long end) {
		URL currentURL;
		
		try {
			currentURL = new URL(urlStr);	
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return null;
		}
		
		if(!validServer(currentURL)) 
			throw new IllegalArgumentException(DIFFERENT_SERVER_EXCEPTION);
		
		try {
				
			byte[] request = getTypeOfRequest(currentURL, start, end);
				
			/* Applying request */
			
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
				if (contentLength >= 0) return in.readNBytes(contentLength);
				else return in.readAllBytes();
				
			} else throw new RuntimeException(String.format("HTTP request failed: [%s]", statusParts[1]));
				
		} catch (Exception x) {
			x.printStackTrace();
		}
		
		return null;
	}
	

	/**
	 * Checks if the new server is valid, that is, if it is the currently opened server.
	 * 
	 * @param newURL - the URL of the requested resource 
	 * @return true if the server corresponds to the current working one, false if otherwise
	 * @throws MalformedURLException - Thrown to indicate that a malformed URL has occurred
	 */
	private boolean validServer (URL newURL){
		int newPort = newURL.getPort() < 0 ? url.getDefaultPort(): newURL.getPort();
		String host = newURL.getHost();
		return port == newPort && host.equals(url.getHost());
	}
	
	/**
	 * Gets the request to be made, in an array of bytes, according to the provided range.
	 * 
	 * @param newURL - the URL of the requested resource
	 * @param start  - the start offset of the requested range
	 * @param end    - the end offset of the requested range (inclusive)
	 * @return request to be made
	 */
	private byte[] getTypeOfRequest (URL newURL, long start, long end) {
		
		if(end == UNDEFINED_RANGE_VALUE) {
			if(start == UNDEFINED_RANGE_VALUE) 
				return String.format(GET_FORMAT_STR, newURL.getPath()).getBytes();
			else 
				return String.format(GET_FORMAT_START_RANGE, newURL.getPath(), start).getBytes();
		} 
		else 
			return String.format(GET_FORMAT_FULL_RANGE, newURL.getPath(), start, end).getBytes();
		
	}

}