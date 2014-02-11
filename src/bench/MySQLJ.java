/* MySQLJ.java - Copyright (c) 2014, David Paul Hentchel
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 * 
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" 
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the specific language 
 * governing permissions and limitations under the License.
 */
package bench;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;


/**
 * Simple driver to run SQL commands.
 * Each line input from stdin is passed to a JDBC connection as a SQL statement.
 * @author dhentchel
 *
 */
public class MySQLJ {

	String _connectURL = null;
	Connection _connection = null;
	boolean _debug = false;

	public MySQLJ( String url, boolean debug ) {
		_connectURL = url;
		_connection = connection(_connectURL);
		_debug = debug;
	}
	
	/**
	 * Create a database connection based on configured parameters.
	 * @return A valid MySQL Connection object
	 */
	public Connection connection ( String url ) {		
		try {
			// Create a connection to the database
			Connection connection = DriverManager.getConnection(url, user, password);
			connection.setAutoCommit(false);
			return connection;
		}
		catch (SQLException e) {
			System.err.println("\n**** Fatal opening connection: " + e.getMessage());
			throw new RuntimeException("Connection failure", e);
		}
	}

	public void close ( ) {
		try {
			_connection.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	List<String> UPDATE_CMDS = Arrays.asList("CREATE", "DROP", "INSERT", "DELETE", "UPDATE", "USE", "SET", "COMMIT");
	/**	
	 * Run an individual non-prepared statement.show
	 */
	public boolean runCommand ( String cmd, PrintStream out ) {
		try {
			if (_debug)
				System.err.println(String.format("Debug> %s", cmd));
			Statement stmt = _connection.createStatement();
			String sqlCmd = cmd.split(" ")[0];
			if (UPDATE_CMDS.contains(sqlCmd.toUpperCase())) { 
				int count = stmt.executeUpdate(cmd);
				out.println(String.format("Update succeeded, count=%d", count));
				return true;
			} else {
				ResultSet result = stmt.executeQuery(cmd);
				renderQueryResults(result, out);
				return true;
			}
		} catch (SQLException e) {
			System.err.println(String.format("Failed executing command %s", cmd));
			e.printStackTrace(System.err);
			return false;
		}
	}

	static final int QUERY_RESULT_SAMPLE_SIZE = 100;
	/* Return String output from a ResultSet for a subset of the results.
	 * todo: support csv and pretty-print options, align tab stops for pretty-print.
	 */
	public void renderQueryResults ( ResultSet result, PrintStream out ) {
		String[] colNames = getColumnNames(result);
		int numFields = colNames.length;
		int[] columnWidth = new int[numFields];
		reviseColumnWidth(columnWidth, colNames);

		// First, pull a limited size sample to get an idea what the column widths are.
		ArrayList<String[]> resultSample = new ArrayList<String[]>();
		int rowCount = 0;
		while (rowCount < QUERY_RESULT_SAMPLE_SIZE) {  
		    String[] rowData = getNextDataLine(result, numFields);
		    if (rowData == null) {
		    	break;
		    } else {
				rowCount++;
				reviseColumnWidth(columnWidth, rowData);
				resultSample.add(rowData);
		    }
		}
		// Now print header lines and Sample rowData.
		out.println(renderBar(columnWidth));
		out.println(renderText(columnWidth, colNames));
		out.println(renderBar(columnWidth));
		Iterator<String[]> itr = resultSample.iterator();
		while (itr.hasNext()) {
			out.println(renderText(columnWidth, itr.next()));
		}
		
		//Continue with additional lines to end of result set
		if (rowCount >= QUERY_RESULT_SAMPLE_SIZE) {
			while (true) {
				String[] rowData = getNextDataLine(result, numFields);
				if (rowData == null)
			    	break;
				rowCount++;
				out.println(renderText(columnWidth, rowData));
			}
		}
		
		//Finally, print trailer and row count
		out.println(renderBar(columnWidth));
		out.println(String.format("Query succeeded, count=%d", rowCount));
	}

	/**
	 * Generate a separator line demarcating header and data.
	 * @param columnWidth Array indicating the length of each column. 
	 * @return A fixed-length string for printing.
	 */
	public String renderBar(int[] columnWidth) {
		StringBuffer buffer = new StringBuffer();
		buffer.append("+");
		for (int i=0; i<columnWidth.length; i++) {
			for (int c=0; c<columnWidth[i]; c++) {
				buffer.append("-");
			}
			buffer.append("+");
		}
		return buffer.toString();
	}

	/**
	 * Generate a text line padded to defined column widths.
	 * @param columnLength Array indicating the length of each column. 
	 * @return A fixed-length string for printing.
	 */
	public String renderText(int[] columnWidth, String[] text) {
		StringBuffer buffer = new StringBuffer();
		buffer.append("|");
		for (int i=0; i<columnWidth.length; i++) {
			String entry = text[i];
			if (entry.length() <= columnWidth[i]) {
				buffer.append(String.format("%1$" + columnWidth[i] + "s", text[i]));
			} else {
				buffer.append(text[i].substring(0, columnWidth[i]-1)).append("*");
			}
			buffer.append("|");
		}
		return buffer.toString();
	}

	/**
	 * Return an array of strings representing the Columns for the result set.
	 */
	String[] getColumnNames ( ResultSet result ) {
		ResultSetMetaData metadata;
		int numFields = 0;
		try {
			metadata = result.getMetaData();
			numFields = metadata.getColumnCount();
			String[] colNames = new String[numFields];
			for (int i=0; i<numFields; i++) {
				colNames[i] = metadata.getColumnName(i+1);
			}
			return colNames;
		} catch (SQLException e) {
				e.printStackTrace();
				throw new RuntimeException("Failure extracting metadata." ,e);
		}
	}
	
	void reviseColumnWidth ( int[] colWidth, String[] colData ) {
		if (colWidth.length != colData.length)
			throw new RuntimeException("Fatal: Mismatch in column count.");
		for (int i=0; i<colWidth.length; i++) {
			if (colData[i].length() > colWidth[i])
					colWidth[i] = colData[i].length();
		}
	}
	
	/* Return a line of data from result set as a string array.0
	 */
	String[] getNextDataLine ( ResultSet result, int numFields ) {
		String[] output = null;
		try {
			if (result.next()) {
				if (output == null)
					output = new String[numFields];
				for (int i=0; i<numFields; i++) {
					Object value = result.getObject(i+1);
					output[i] = value.toString();
				}
			}
			return output;
		} catch (SQLException e) {
			e.printStackTrace();
			throw new RuntimeException("Failure extracting data." ,e);
		}
	}

	/**
	 * Initialize the MySQL JDBC driver.
	 */
	public static void loadDriver( ) {
		try {
		// Load the JDBC driver
		Class.forName("com.mysql.jdbc.Driver");
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Failed loading driver", e);
		}
	}
	
	/**
	 * Parse runtime command arguments.
	 */
	static String host = "localhost";
	static int    port = 3306;
	static String dbname = null;
	static String user = "root";
	static String password = "password";
	static boolean debug = false;

	public static final String HELP_TEXT = "Simple SQL Driver" +
			"Syntax:  java com.parelastic.bench.test.MySQLJ [-help] [-debug] [param=value] ... " +
			"\nWhere param can be:" +
			"\n\thost - default: " + host +
			"\n\tport - default: " + port +
			"\n\tdbname - default: (none)" +
			"\n\tuser - default: " + user +
			"\n\tpassword - default: " + password;
	
	public static void parseCommandArgs (String[] args) {
		boolean exitAfterParse = false;
		for (int i=0; i<args.length; i++) {
			String arg = args[i];
			if (arg.startsWith("-")) {
				if (arg.equalsIgnoreCase("-help")) {
					System.err.println(HELP_TEXT);
					exitAfterParse = true;
				} else if (arg.equalsIgnoreCase("-debug")) {
					debug = true;
				} else {
					System.err.println("Unrecognized command flag: " + arg);
					System.err.println(HELP_TEXT);
					exitAfterParse = true;
				}
			}
			else if (arg.contains("=")) {
				String[] w = arg.split("=", 2);
				if (w[0].equalsIgnoreCase("host")) {
					host = w[1];
				} else if (w[0].equalsIgnoreCase("port")) {
					port = Integer.parseInt(w[1]);
				} else if (w[0].equalsIgnoreCase("dbname")) {
					dbname = w[1];
				} else if (w[0].equalsIgnoreCase("user")) {
					user = w[1];
				} else if (w[0].equalsIgnoreCase("password")) {
					password = w[1];
				} else {
					System.err.println("Invalid command argunment: " + arg);
					System.err.println(HELP_TEXT);
					exitAfterParse = true;
				}
			} else {
				System.err.println("Invalid command argunment: " + arg);
				System.err.println(HELP_TEXT);
				exitAfterParse = true;
			}
		}
		if (exitAfterParse)
			System.exit(0);
	}

	/**
	 * Connect to the configured database and execute user-provided input as SQL commands.
	 * @param args [-help] [-debug] [host=VAL] [port=VAL] [dbname=VAL] [user=VAL] [password=VAL]
	 */
	public static void main(String[] args) {
		parseCommandArgs(args);
		String connectUrl = null;
		if (dbname == null)
			connectUrl = String.format("jdbc:mysql://%s:%d", host, port);
		else
			connectUrl = String.format("jdbc:mysql://%s:%d/%s", host, port, dbname );
		
		loadDriver();
		MySQLJ driver = new MySQLJ(connectUrl, debug);
		Scanner scanIn = new Scanner(System.in);
		
		boolean keepGoing = true;
		while (keepGoing) {
			boolean more = true;
			String cmd = "";
			System.out.print("mysqlj> ");
			while (more){
				String input = scanIn.nextLine();
				if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("quit;")) {
					keepGoing = false;
					more = false;
				} else {
						if (input.endsWith(";")){
						cmd += input.substring(0, (input.length() - 1));
						more = false;
					} else if (input.endsWith("\\G")){
						cmd += input.substring(0, (input.length() - 2));
						more = false;
					} else {
						cmd += input;
						cmd += " ";
						System.out.print("   -> ");
					}
				}	
			}
			if (keepGoing) {
				boolean succeeded = driver.runCommand(cmd.trim(), System.out);
				if (!succeeded)
					System.out.println(String.format("\nCommand Failed.\n"));
			}
		}
		scanIn.close();
		System.out.println("Done.");
	}

}