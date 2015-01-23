package FTPSnooper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FTPSnooper {
	
	int FTP_SERVER_PORT = 21;

	
	// Hostname of the FTP server to connect to.
	private final String hostname;
	
	// Directory on server to analyze.
	private final String directory;
	
	// Pattern object for file regular expression.
    private final Pattern filePattern;
    
    private static boolean DEBUG = false;
    
	// Results that are generated with filenames given as the keys
	// and 'first 20 lines of each file' given as the values.
	// See fetch() method for dummy example data of layout.
	private final HashMap<String, String> fileInfo = new HashMap<String, String>();
	
	/*
	public static void main (String args[]){
		
		
		FTPSnooper ftpSnooper = new FTPSnooper("ftp.cs.ucl.ac.uk", "rfc", "rfc95[7-9]\\.txt");
		try {
			ftpSnooper.fetch();
		} catch (IOException e) {
			System.out.println("Problem with fetching");
			e.toString();
		}
	}
	*/
	public FTPSnooper(String hostname, String directory,
			String filenameRegularExpression) {
		this.hostname = hostname;
		this.directory = directory;
		this.filePattern = Pattern.compile(filenameRegularExpression);
	}
	
	
	
	/**
	 * Fetch the required file overviews from the FTP server.
	 * 
	 * @throws IOException
	 */
	public void fetch() throws IOException {
		
		Socket socket = connect(hostname ,FTP_SERVER_PORT,"anonymous", "ident");
		changeDirectory(directory, socket);
		String fileList = listDirectories(socket);
		ArrayList<String> fileNames = applyRegex(fileList);
		if (fileNames.size() == 0){
			System.out.println("No Files found");
		}else{
			for (String file : fileNames){
				fileInfo.put(file,getFile(socket, file));
			}
		}
		disconnect(socket);
	}

	
	/**
	   * Connects to an FTP server and logs in with the supplied username and
	   * password.
	   */
	  public Socket connect(String host, int port, String user,String pass) throws IOException {
		
		Socket socket = new Socket(host, port);
		  
	    String response = readLine(socket);
	    if (!response.startsWith("220 ")) {
	      throw new IOException(
	          "SimpleFTP received an unknown response when connecting to the FTP server: "
	              + response);
	    }

	    sendLine("USER " + user, socket);

	    response = readLine(socket);
	    if (!response.startsWith("331 ")) {
	      throw new IOException(
	          "SimpleFTP received an unknown response after sending the user: "
	              + response);
	    }

	    sendLine("PASS " + pass, socket);

	    response = readLine(socket);
	    if (!response.startsWith("230 ")) {
	      throw new IOException(
	          "SimpleFTP was unable to log in with the supplied password: "
	              + response);
	    }
	    
	    return socket;
	  }
	  
	  public void disconnect(Socket socket) throws IOException{
		  try {
		      sendLine("QUIT", socket);
		      socket.close();
		    } finally {
		      socket = null;
		    }
	  }
	
	  public int extractDataPort(String dataResponse){
  		  
		  String iP = dataResponse.substring(dataResponse.lastIndexOf("(")+1, dataResponse.lastIndexOf(")"));
		  String parts[] = iP.split(",");
		  int port = (Integer.valueOf(parts[4]) * 256) + Integer.valueOf(parts[5]);
		  System.out.println("Data Port: " + port);
		  return port;
	}
	  
	  public ArrayList<String> applyRegex(String input){
			 Matcher m = filePattern.matcher(input);
			 ArrayList<String> fileNames = new ArrayList();
			 while (m.find()) {
				 fileNames.add(m.group());
			}
			
			for (String file : fileNames){
				System.out.println(file);
			}
			
			return fileNames;
	 }
	  
	  private void changeDirectory (String directoryName, Socket socket) throws IOException{
		  
		  	sendLine("CWD " + directoryName , socket);
			String response = readLine(socket);
			if (!response.startsWith("250 ")) {
			  throw new IOException(
			      "Couldn't change Directory: " + response);
			}
	  }
	  
	  
	  public Socket enterPassiveMode(Socket socket) throws IOException{
		
		//Enter Passive Mode
		System.out.println("Attempting to Enter Passive Mode");
	    sendLine("PASV" , socket);
	    String response = readLine(socket);
	    if (!response.startsWith("227 ")) {
	      throw new IOException(
	          "Couldn't Enter Passive Mode: " + response);
	    }
	    
		Socket dataSocket = new Socket(hostname, extractDataPort(response));
	    
	    return dataSocket;
	    
	  }
	  
	  
	  private String listDirectories(Socket socket) throws IOException{
		  Socket dataSocket = enterPassiveMode(socket);
		  
		  sendLine("TYPE A" , socket);
		  String controlResponse = readLine(socket);
		  
		  sendLine("NLST", socket);
		  controlResponse = readLine(socket);
		  
		  if (!controlResponse.startsWith("150 ")) {
		      throw new IOException(
		          "Couldn't List Files: " + controlResponse);
		  }
		  
		  String dataResponse = readMultipleLines(0, dataSocket);
		  
		 
		  
		  controlResponse = readLine(socket);
		  return dataResponse;
	  }
	  
	  private String getFile(Socket socket, String file) throws IOException{
		  
		 
		  
		  Socket dataSocket = enterPassiveMode(socket);
		  
		  sendLine("TYPE A" , socket);
		  String controlResponse = readLine(socket);
		  
		  if (!controlResponse.startsWith("200 ")) {
		      throw new IOException(
		          "Couldn't Set File type: " + controlResponse);
		  }
		  
		  
		  sendLine("RETR " + file , socket);
		  controlResponse = readLine(socket);
		  
		  if (!controlResponse.startsWith("150 ")) {
		      throw new IOException(
		          "Couldn't Load File: " + controlResponse);
		  }
		  
		  String dataResponse = readMultipleLines(20, dataSocket);
		  
		  
		  controlResponse = readLine(socket);
		  return dataResponse;
	  }
	  
	  private String readLine(Socket socket) throws IOException {
		  
		  	BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		  	
		    String line = reader.readLine();
		    if (DEBUG) {
		      System.out.println("< " + line);
		    }
		    return line;
	  }
	  
	  private String readMultipleLines(int lineLimit, Socket socket) throws IOException {
		  
		  BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		  String line = null;
		  String clientCommand = null;
		  
		  int i =0;
		  while ((line = reader.readLine ()) != null) {
			
			  if(line.length() != 0){
				  clientCommand += line;
			      clientCommand += "\n\r";
			      if(lineLimit > 0){
			      }
				  if (lineLimit > 0){
					  if ( i >= lineLimit){
						  break;
					  }else{
						  i++;
					  }
				  }
			  }
			  
		      
		  }
		  reader.close();
		  		  return clientCommand;

		  		/*
		  int size = -1;
		  byte[] buffer = new byte[5*1024]; // a read buffer of 5KiB
		  byte[] byteData;
		  StringBuilder fullData = new StringBuilder();
		  String stringData;
		  int i = 0;
		  while ((size = socket.getInputStream().read(buffer)) > -1) {
			  byteData = new byte[size];
		      System.arraycopy(buffer, 0, byteData, 0, size);
		      stringData = new String(byteData);
		      fullData.append(stringData);
		      if (lineLimit > 0){
		    	  if(i > lineLimit){
		    		  break;
		    	  }else{
		    		 if (stringData.contains("\n")){
		    			 i++;
		    		 }
		    	  }
		    	  
		      }
		  }
		  return fullData.toString();
		   */
		  
		    
	  }
	  
	  /**
	   * Sends a raw command to the FTP server.
	   */
	  private void sendLine(String line, Socket socket) throws IOException {
		

	    if (socket == null) {
	      throw new IOException("FTP is not connected.");
	    }
		  
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
		
	    try {
	      writer.write(line + "\r\n");
	      writer.flush();
	      if (DEBUG) {
	        System.out.println("> " + line);
	      }
	    } catch (IOException e) {
	      socket = null;
	      throw e;
	    }
	  }
	
	/**
	 * Return the result of the fetch command.
	 * @return The result as a map with keys = "filenames" and values = "first 20 lines of each file".
	 */
	public Map<String, String> getFileInfo() {
		return fileInfo;
	}
	
	
	
	
	//==================================================================================================//
	
	
	public class DataThread extends Thread{
		
	}
	
}

