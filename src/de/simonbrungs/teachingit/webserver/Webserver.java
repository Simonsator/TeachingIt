package de.simonbrungs.teachingit.webserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.simonbrungs.teachingit.TeachingIt;
import de.simonbrungs.teachingit.api.events.ContentCreateEvent;
import de.simonbrungs.teachingit.api.events.HeaderCreateEvent;
import de.simonbrungs.teachingit.api.events.WebsiteCallEvent;
import de.simonbrungs.teachingit.api.users.User;

public class Webserver {
	private boolean shouldStop = false;
	private Thread webserverThread;
	private HashMap<String, File> registerdFiles = new HashMap<>();

	public Webserver(String pAdress, int pPort) {
		webserverThread = new Thread(new Runnable() {
			public void run() {
				starWebserver(pPort);
			}
		});
		webserverThread.start();
	}

	public void starWebserver(int pPort) {
		try {
			try (ServerSocket serverSocket = new ServerSocket(pPort)) {
				while (!shouldStop)
					try (Socket socket = serverSocket.accept();
							InputStream input = socket.getInputStream();
							BufferedReader reader = new BufferedReader(new InputStreamReader(input));
							OutputStream output = socket.getOutputStream();
							PrintWriter writer = new PrintWriter(new OutputStreamWriter(output))) {
						/*
						 * String line = reader.readLine(); String useragent =
						 * "";
						 * 
						 * if (line != null) while (!line.isEmpty()) { line =
						 * reader.readLine(); if
						 * (line.startsWith("User-Agent:")) { useragent = line;
						 * } System.out.println(line); }
						 */
						String path = receiveRequest(reader);
						System.out.println("request from " + socket.getRemoteSocketAddress() + " to path " + path);
						User user = new User(path, null, socket.getRemoteSocketAddress());
						WebsiteCallEvent websiteCallEvent = new WebsiteCallEvent(null);
						TeachingIt.getInstance().getEventExecuter().executeEvent(websiteCallEvent);
						if (!websiteCallEvent.isCanceld()) {
							File file = registerdFiles.get(path);
							if (file != null) {
								List<String> lines = Files.readAllLines(Paths.get(path));
								writer.println("HTTP/1.0 200 OK");
								writer.println("Content-Type: text/html; charset=ISO-8859-1");
								writer.println("Server: NanoHTTPServer");
								writer.println();
								String response = lines.get(0);
								lines.remove(0);
								for (String line : lines)
									response += "\n" + line;
								writer.println(response);
							} else {
								String response = "<html><head>";
								HeaderCreateEvent headerCreateEvent = new HeaderCreateEvent();
								TeachingIt.getInstance().getEventExecuter().executeEvent(headerCreateEvent);
								if (headerCreateEvent.getHeader() != null) {
									response += headerCreateEvent.getHeader();
								}
								response = response
										+ TeachingIt.getInstance().getPluginManager().getTheme().getHeader();
								ContentCreateEvent contentCreateEvent = new ContentCreateEvent(user);
								TeachingIt.getInstance().getEventExecuter().executeEvent(contentCreateEvent);
								if (contentCreateEvent.getTitle() == null) {
									contentCreateEvent.setTitle("Teaching IT");
								}
								if (contentCreateEvent.getContent() == null) {
									contentCreateEvent = TeachingIt.getInstance().getPluginManager().getTheme()
											.getErrorPageGenerator().getErrorPageNotFound(contentCreateEvent);
								}
								response += "<title>" + contentCreateEvent.getTitle() + "</title>" + "</head>"
										+ TeachingIt.getInstance().getPluginManager().getTheme().getBodyStart(user)
										+ contentCreateEvent.getContent()
										+ TeachingIt.getInstance().getPluginManager().getTheme().getBodyEnd(user)
										+ "</body></html>";
								writer.println("HTTP/1.0 200 OK");
								writer.println("Content-Type: text/html; charset=ISO-8859-1");
								writer.println("Server: NanoHTTPServer");
								writer.println();
								writer.println(response);
							}
						}
					} catch (IOException iox) {
					}
			} catch (BindException e) {
				e.printStackTrace();
				TeachingIt.getInstance().shutDown(1);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private String receiveRequest(BufferedReader reader) throws IOException {
		final Pattern getLinePattern = Pattern.compile("(?i)GET\\s+/(.*?)\\s+HTTP/1\\.[01]");
		String resource = null;
		for (String line = reader.readLine(); !line.isEmpty(); line = reader.readLine()) {
			Matcher matcher = getLinePattern.matcher(line);
			if (matcher.matches())
				resource = matcher.group(1);
		}
		return resource;
	}

	public void registerFile(File pFile, String pURL) {
		registerdFiles.put(pURL, pFile);
	}

	public boolean isURLRegisterd(String pURL) {
		return registerdFiles.containsKey(pURL);
	}

	public boolean unregisterFile(String pURL) {
		return registerdFiles.remove(pURL) != null;
	}

	@SuppressWarnings("deprecation")
	public void stop() {
		shouldStop = true;
		webserverThread.stop();
	}
}
