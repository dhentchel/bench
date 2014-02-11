/* Session.java - Copyright (c) 2014, David Paul Hentchel
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;

enum CMD_TYPE{
	COMMENT,  // SQL comment, must be of format "-- any text..."; in Verbose mode these are printed to stderr.
	DDL,      // Manipulates (CREATE, DROP, ALTER) databases and tables.
	PREPARED, // Creating, executing and destroying PreparedStatements; note: uses extended syntax.
	DYNAMIC,  // Standard SQL, especially SELECT, INSERT, UPDATE, DELETE.
	SESSION;  // Commands affecting session state, including transactions, connections, variables.
}

/**
 * The Session executes all the SQL commands for the client.
 * It encapsulates the SQL Connection used for execution and maintains the state of transactions so it can recover as needed.
 * The execute() method analyzes what the CMD_TYPE of the input command string is and dispatches accordingly. Any non-successful
 * operation triggers a RuntimeException.
 * todo option to anaylze result sets (row counts and/or data size).
 * todo perform transaction cleanup as needed.
 * @author dhentchel
 *
 */
public class Session {
	Connection _connection = null;
	long _clientID;
	public long clientID() { return _clientID; }
	boolean _inTransaction = false;
	String _currentDatabase = null;
	
	long _errorCount = 0;  // todo: maybe add a warnCount too?
	public long errorCount() { return _errorCount; }
	long _updateCount = 0;
	public long updateCount() { return _updateCount; }
	long _queryCount = 0;
	public long queryCount() { return _queryCount; }
	long _statementCount = 0;
	public long statementCount() { return _statementCount; }
	
	public Session ( DatabaseManager dbmgr, long clientID ) {
		_connection = dbmgr.connection();
		_clientID = clientID;
		_currentDatabase = dbmgr.defaultDatabase();
	}
	
	public void endSession ( ) {
		if (_inTransaction) {
			execute("COMMIT");
			_inTransaction = false;
		}
		try {
			if (_connection != null) {
				_connection.close();
			}
		} catch (SQLException e) {
			System.err.println(String.format("Session: Warning, error closing connection - %s", e.getMessage()));
		}
		_connection = null;
	}

	/**
	 * Determine the type of statement and execute it within the session's connection.
	 * Prepared Statements are cached and executed based on the PREPARE and EXECUTE syntax.
	 * Note that semicolons separators are not supported in JDCB, so any terminating semicolon is stripped off.
	 * @return A boolean indicating whether the session has been terminated (e.g. a 'quit' command).
	 */
	public boolean execute ( String sqlIn ) {
		boolean status = true;
		String sqlString = sqlIn.trim();
		if (sqlString.endsWith(";"))
			sqlString = sqlString.substring(0, sqlString.length() - 1);
		String sqlCommand = sqlString.split(" ", 2)[0].toUpperCase().trim();
		CMD_TYPE type;
		if (CommandType.containsKey(sqlCommand)) {
			type = CommandType.get(sqlCommand);
		} else {
			throw new RuntimeException(String.format("Session: Cannot identify SQL command beginning with %s", sqlCommand));
		}
		_statementCount++;
		// Switch behavior based on command type.
		switch (type) {
		case COMMENT:
			status = true;
			if (TestContext.isVerbose)
				System.err.println(sqlIn);
			break;
		case DDL:
			status = executeDDL(sqlCommand, sqlString);
			break;
		case PREPARED:
			status = executePREPARED(sqlCommand, sqlString);
			break;
		case DYNAMIC:
			status = executeDYNAMIC(sqlCommand, sqlString);
			break;
		case SESSION:
			status = executeSESSION(sqlCommand, sqlString);
			break;
		default:
			_errorCount++;
			System.err.println(String.format("Session: Command %s not implemented; full SQL statement is \n\t%s", sqlCommand, sqlIn));
		}
		return status;
	}
	
	private boolean executeSESSION(String sqlCommand, String sqlIn) {
		boolean status = true;
		if (sqlCommand.equalsIgnoreCase("QUIT") || sqlCommand.equalsIgnoreCase("EXIT")) {
			endSession();
			return false;
		}
		else if (sqlCommand.equals("CONNECT") || sqlCommand.equals("DISCONNECT")) {
			System.err.println(String.format("Session: Warning, CONNECT/DISCONNECT not supported; ignoring statement %s.", sqlIn));
		}

		try {
			Statement stmt= _connection.createStatement();
			stmt.execute(sqlIn);
			if (sqlCommand.equals("USE")) {
				String [] part = sqlIn.split("[\\s]+", 2);
				_currentDatabase = part[1];
			}
			else if (sqlCommand.equals("BEGIN")) {
				_inTransaction = true;
			}
			else if (sqlCommand.equals("COMMIT") || sqlCommand.equals("ABORT")) {
				_inTransaction = false;
			}
		} catch (SQLException e) {
			_errorCount++;
			throw new RuntimeException(String.format("Session: Fatal executing sessiond statement: %s", sqlIn), e);
		}

		return status;		
	}

	/**
	 * Execute a SQL command dynamically.
	 * This includes the typical DML commands, the SELECT and update operations on tables.
	 * Little additional validation and processing is done on these, they are simply passed through.
	 * todo: process result sets and gather stats.
	 * @param sqlCommand The generic command (i.e. the first word in the command string)
	 * @param sql The full SQL command string.
	 * @return always returns true; throws a runtime exception otherwise.
	 */
	private boolean executeDYNAMIC(String sqlCommand, String sqlIn) {
		boolean status = true;
		try {
			Statement stmt= _connection.createStatement();
			stmt.execute(sqlIn);
			if (sqlCommand.equals("SELECT"))
				_queryCount++;
			else
				_updateCount++;
		} catch (SQLException e) {
			throw new RuntimeException(String.format("Session: Fatal executing dynamic statement: %s", sqlIn), e);
		}
		return status;		
	}

	static HashMap<String,PreparedStatement> preparedStmt = new HashMap<String,PreparedStatement>();
	static HashMap<String,Boolean> preparedStmtIsUpdate = new HashMap<String,Boolean>();
	/**
	 * Execute operations on prepared statements.
	 * Prepared statement syntax must conform to one of the following:
	 * <p>PREPARE stmt1 FROM 'some_query_stmt ...';</p>
	 * <p>EXECUTE stmt1 USING @arg1, @arg2, ...;</p>
	 * <p>DEALLOCATE PREPARE stmt1;</p>
	 * NOTE: In the EXECUTE syntax, the arg must be either a simple literal (with no embedded comma or quote signs) or a
	 * quoted string using the single quote character.  To embed a single quote within the quoted string, use a double-single-quote.
	 * For example, the string "'my son's face'" would be entered as:  '''my son''s face'''.
	 * @param sqlCommand
	 * @param sqlIn
	 * @return
	 */
	private boolean executePREPARED(String sqlCommand, String sqlIn) {
		boolean status = true;
		if (sqlCommand.equals("PREPARE")) {
			String [] part = sqlIn.split("[\\s]+", 4);
			if (! part[2].toUpperCase().equals("FROM"))
				throw new RuntimeException(String.format("Session: Illegal PREPARE syntax: %s", sqlIn));
			String sqlText = part[3].trim();
			if ("'`\"".contains(sqlText.substring(0, 1))) {  // Handle surrounding quotes {
				if (sqlText.substring(0, 1).equals(sqlText.substring(sqlText.length()-1)))
					sqlText = sqlText.substring(1, sqlText.length() - 1);
				else {
					throw new RuntimeException(String.format("Session: PREPARE statement not correctly quoted: %s", sqlIn));
				}
			}
			PreparedStatement stmt;
			try {
				stmt = _connection.prepareStatement(sqlText);		CommandType.put("DEFAULT", CMD_TYPE.SESSION);

			} catch (SQLException e) {
				throw new RuntimeException(String.format("Session: Fatal preparing statement: %s", sqlIn), e);
			}
			preparedStmt.put(part[1], stmt);
			if (sqlText.split("[\\s]+", 2)[1].equalsIgnoreCase("SELECT"))
				preparedStmtIsUpdate.put(part[1], false);
			else
				preparedStmtIsUpdate.put(part[1], true);
		} else if (sqlCommand.equals("EXECUTE")) {
			String [] part = sqlIn.split("[\\s]+", 4);
			if (! part[2].toUpperCase().equals("USING"))
				throw new RuntimeException(String.format("Session: Illegal EXECUTE syntax: %s", sqlIn));
			if (!preparedStmt.containsKey(part[1])) {
				throw new RuntimeException(String.format("Session: Cannot execute prepared statement %s, statement not prepared; full SQL statement is \n\t%s", part[1], sqlIn));
			}
			PreparedStatement stmt = preparedStmt.get(part[1]);
			String[] args = parseExecuteArgs(part[3]);
			try {
				for (int i=0; i<args.length; i++) {
					stmt.setString(i+1, args[i]);  //todo: do we need to strip quotes?
				}
				stmt.execute();
				if (preparedStmtIsUpdate.get(part[1]).booleanValue())
					_updateCount++;
				else
					_queryCount++;
			} catch (SQLException e) {
				throw new RuntimeException(String.format("Session: Fatal executing prepared	 statement: %s", sqlIn), e);
			}
		} else if (sqlCommand.equals("DEALLOCATE")) {
			String [] part = sqlIn.split("[\\s]+", 3);
			if (! part[1].toUpperCase().equals("PREPARE"))
				throw new RuntimeException(String.format("Session: Illegal PREPARE syntax: %s", sqlIn));
			if (!preparedStmt.containsKey(part[1])) {
				System.err.println(String.format("Session: Warning, script deallocates prepared statement %s, but statement does not exist.", part[2]));
			}
		}

		return status;		
	}

	/**
	 * Parse EXECUTE args.
	 * If there are no non-whitespace characters, a single empty string "" is returned.
	 * Only single quote "'" is recognized as a quote character.
	 * Whitespace preceding the arg list or immediately following a comma is stripped.
	 * If the first non-whitespace char of an arg is NOT a quote, all characters up to the next comma are included in the value,
	 * (i.e. you cannot escape comma or quote characters unless you quote the entire arg).
	 * If the arg is quoted, then any double-quote AFTER the initial one, is taken as an escaped, literal single-quote.
	 * Within the quoted arg, commas are taken as literal.  If the close quote is not found an exception is thrown.
	 * The next non-whitespace character after a close quote for a quoted arg must be a comman or an exception is thrown. 
	 * @param argStr The input for the arguments, i.e. the text following the "EXECUTE <PreparedStmt> USING " tokens.
	 * @return An array of Strings, one for each prepared statement parameter.
	 */
	private String[] parseExecuteArgs ( String argStr ) {
		String input = argStr.trim();
		int offset = 0;
		ArrayList<String> args = new ArrayList<String>();
		while (true) {
			if (offset == input.length()) {
				args.add("");
				break;
			}
			if (input.substring(offset, offset+1).equals(",")) {
				args.add("");
				offset++;
			} else if (input.substring(offset, offset+1).equals("'")) {  //quoted literal
				StringBuffer arg = new StringBuffer("");
				boolean closedQuote = false;
				offset++;
				while (!closedQuote) {
					int nextQuote = input.indexOf("'", offset);
					if (nextQuote < 0)
						throw new RuntimeException("Session: Cannot parse arguments, closing quote not found.");
					if (nextQuote < input.length()-1 && input.substring(nextQuote+1, nextQuote+2).equals("'")) { // escaped quote char
						arg.append(input.substring(offset, nextQuote+1));
						offset = nextQuote+2;
					} else {  // closing quote
						arg.append(input.substring(offset, nextQuote));
						args.add(arg.toString());
						offset = nextQuote+1;
						closedQuote = true;
					}
				}
				while (offset < input.length() && Character.isWhitespace(input.substring(offset, offset+1).charAt(0)))
					offset++;
				if (offset == input.length())
					break;  // last arg done
				if (input.substring(offset, offset+1).equals(",")) {  // clear subsequent comma
					offset++;
				} else {
					throw new RuntimeException("Session: Parsing EXECUTE args, cannot have characters after quoted arg value.");
				}				
			} else { // non-quoted string
				int nextComma = input.indexOf(",", offset);
				if (nextComma < 0) { // last arg
					args.add(input.substring(offset, input.length()));
					break;
				} else {
					args.add(input.substring(offset, nextComma));
					offset = nextComma+1;
				}
				
			}
			while (offset < input.length() && Character.isWhitespace(input.substring(offset, offset+1).charAt(0)))
				offset++;
		}

	    return args.toArray(new String[args.size()]);
	}

	private boolean executeDDL(String sqlCommand, String sqlIn) {
		boolean status = true;
		try {
			Statement stmt= _connection.createStatement();
			stmt.execute(sqlIn);
		} catch (SQLException e) {
			throw new RuntimeException(String.format("Session: Fatal executing DDL statement: %s", sqlIn), e);
		}

		return status;		
	}

	/**
	 * Manage SQL request metadata.
	 */
	static HashMap<String, CMD_TYPE> CommandType;
	static {
		CommandType = new HashMap<String, CMD_TYPE>();
		// SQL Comment
		CommandType.put("--", CMD_TYPE.COMMENT);
		// DDL commands manipulate schema and tables
		CommandType.put("CREATE", CMD_TYPE.DDL);
		CommandType.put("ALTER	", CMD_TYPE.DDL);
		CommandType.put("DROP", CMD_TYPE.DDL);
		CommandType.put("SHOW", CMD_TYPE.DDL);
		CommandType.put("DESCRIBE", CMD_TYPE.DDL);
		// DYNAMIC SQL statements, executed directly
		CommandType.put("SELECT", CMD_TYPE.DYNAMIC);
		CommandType.put("UPDATE", CMD_TYPE.DYNAMIC);
		CommandType.put("INSERT", CMD_TYPE.DYNAMIC);
		CommandType.put("DELETE", CMD_TYPE.DYNAMIC);
		CommandType.put("TRUNCATE", CMD_TYPE.DYNAMIC);
		// PREPARED statement operations
		CommandType.put("PREPARE", CMD_TYPE.PREPARED);
		CommandType.put("EXECUTE", CMD_TYPE.PREPARED);
		CommandType.put("DEALLOCATE	", CMD_TYPE.PREPARED);
		// SESSION commands manage metadata
		CommandType.put("USE", CMD_TYPE.SESSION);
		CommandType.put("BEGIN", CMD_TYPE.SESSION);
		CommandType.put("COMMIT", CMD_TYPE.SESSION);
		CommandType.put("ABORT", CMD_TYPE.SESSION);
		CommandType.put("CONNECT", CMD_TYPE.SESSION);
		CommandType.put("DISCONNECT", CMD_TYPE.SESSION);
		CommandType.put("CONSTRAINT", CMD_TYPE.SESSION);
		CommandType.put("GRANT", CMD_TYPE.SESSION);
		CommandType.put("REVOKE", CMD_TYPE.SESSION);
		CommandType.put("QUIT", CMD_TYPE.SESSION);
		CommandType.put("EXIT", CMD_TYPE.SESSION); // Convenience alias for QUIT

	}
	
	
}
