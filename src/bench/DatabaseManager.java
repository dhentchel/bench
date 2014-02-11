/* DatabaseManager.java - Copyright (c) 2014, David Paul Hentchel
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 * 
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" 
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the specific language 
 * governing permissions and limitations under the License.
 */package bench;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Random;

/**
 * Singleton utility class to abstract and manage SQL Database operations.
 * <ol>This consists of several functions:
 * </li>Manage parameters specific to the database and ParElastic</li>
 * </li>Handle create/destroy of Databases, Tables, etc</li>
 * </li>Provide an abstract way to construct and run arbitrary SQL syntax</li>
 * </li>Generate simulated data and implement various statistical distributions</li>
 * </li>Pass statement out and manage Connections and connection pools</li>
 * </li>Database monitoring and logging functions</li>
 * </ol>
 * @author dhentchel
 *
 */
public class DatabaseManager {
	TestContext _ctx = null;
	Random _randGen =  null;
	String _connectURL = null;
	String _dbname;
	public String defaultDatabase() { return _dbname; }
	String _user;
	String _password;

	public DatabaseManager ( TestContext ctx) {
		_ctx = ctx;
		_user = _ctx.getString("user");
		_password = _ctx.getString("password");
		_connectURL = String.format("jdbc:mysql://%s:%s/%s", _ctx.getString("host"), _ctx.getString("port"), _ctx.getString("dbname") );
		_randGen = new Random();

		loadDriver();
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
	 * Create a database connection based on configured parameters.
	 * @return A valid MySQL Connection object
	 */
	public Connection connection ( ) {		
		try {
			// Create a connection to the database
			Connection connection = DriverManager.getConnection(_connectURL, _user, _password);
			if (TestContext.isVerbose)
				if (TestContext.isVerbose) System.err.println("Connecting to database at " + _connectURL);
			return connection;
		}
		catch (SQLException e) {
			System.err.println("\n**** Fatal opening connection: " + e.getMessage());
			throw new RuntimeException("Connection failure", e);
		}
	}

	
	/**
	 * Return a prepared statement for th_ctxe thread's local connection.
	 * @param statementStr Any SQL statement, including ? params as needed.
	 * @return
	 */
	public PreparedStatement prepareStatement( String statementStr, Connection connection ) {
		PreparedStatement stmt = null;
		
		try {
			stmt = connection.prepareStatement(statementStr);
		} catch (SQLException e) {
			throw new RuntimeException("Failed to prepare statement");
		}
		
		return stmt;
	}

	/**
	 * Set parameters for the SURVEY_INSERT prepared statement.
	 * The parameter values are generated randomly, to ensure foreign keys are unique.
	 * @param stmt A prepared statement returned by the createStatement method.
	 * @throws SQLException
	 */
	public void setStatementValues ( PreparedStatement stmt, String[] values ) throws SQLException {
		for (int i=1; i<=values.length; i++) {
			stmt.setString(1, values[i]);
		}
	}

}
