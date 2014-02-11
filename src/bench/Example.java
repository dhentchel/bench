package bench;
import java.sql.*;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Example prototype for a multi-client SQL benchmark.
 * This benchmark performs repetitive INSERT operations against a single table, committing after each insert.
 * It is run from the command line, for example:
 * <pre>java com.parelastic.bench.test.Example -debug host=localhost port=3307
 * </pre>
 * You can vary database size, schema definitions and number of client threads by modifying the code and recompiling.
 * @author dhentchel
 *
 */
public class Example implements Runnable {

	static final String SURVEY_INSERT = "INSERT INTO SurveyResults (attendeeId, surveySessionId, " +
			"surveyId, surveyQuestionId, surveyAnswerId, answer, Contexts_id) VALUES " +
			"(?, ?, 'Survey#323458234', 'SurveyQuestion#1234567', 'SurveyAnswer#98475765', ?, ?);";
	static final String VERSION = "1.3"; // Corrected schema def

	String _connectURL = null;
	Connection _connection = null;
	Statement  _statement = null;
	String     _threadID = "Undefined";
	Random     _randGen =  null;
	AtomicBoolean _runFlag = null;
	boolean _debugFlag = false;
	int _insertsPerCommit = 1;
	volatile long _txnCount = 0;
	volatile long _errCount = 0;
	public long txnCount() {return _txnCount;}
	public long errCount() {return _errCount;}
				
	public Example( AtomicBoolean runFlag, String threadID, boolean debugFlag, String url, int insertsPerCommit ) {
		_runFlag = runFlag;
		_debugFlag = debugFlag;
		_threadID = threadID;
		_randGen = new Random();
		_connectURL = url;
		_insertsPerCommit = insertsPerCommit;
	}
	
	/**
	 * Create a database connection based on configured parameters.
	 * @return A valid MySQL Connection object
	 */
	public Connection connection ( String url ) {		
		try {
			// Create a connection to the database
			Connection connection = DriverManager.getConnection(url, user, password);
			if (_insertsPerCommit == 0)
				connection.setAutoCommit(true);
			else
				connection.setAutoCommit(false);
			if (debug)
				System.err.println("Connecting to database at " + url);
			return connection;
		}
		catch (SQLException e) {
			System.err.println("\n**** Fatal opening connection: " + e.getMessage());
			throw new RuntimeException("Connection failure", e);
		}
	}

	/**
	 * Pre-load the database to a realistic size, populating indexes.
	 * This ensures that indexes are populated and therefore record contention will be manageable when the peak load test starts.
	 * Insert operations are batched to improve performance.
	 */
	public void initializeDatabase( long recordCount ) {
		String clearAll = "DROP TABLE IF EXISTS SurveyResults;";
		String createTable = "CREATE TABLE SurveyResults ( id int(11) unsigned NOT NULL AUTO_INCREMENT, attendeeId varchar(45) DEFAULT NULL," +
		" surveySessionId varchar(45) NOT NULL, surveyId varchar(45) DEFAULT NULL, surveyQuestionId varchar(45) NOT NULL," +
		" surveyAnswerId varchar(45) DEFAULT NULL, answer text, sentTime int(11) DEFAULT 0, qmTest tinyint(2) NOT NULL DEFAULT 0," +
		" Contexts_id int(11) unsigned NOT NULL, qmActive tinyint(1) NOT NULL DEFAULT 1, PRIMARY KEY (id));";

		long insertCount = 0;
		long startLoad = System.currentTimeMillis();
		if (_connection == null){
			_connection = connection(_connectURL);
		}

		try {
			System.err.println("\nDestroying any existing data and recreating table.");
			Statement stmt = _connection.createStatement();
			stmt.executeUpdate(clearAll);
			_connection.commit();
			stmt.executeUpdate(createTable);
			_connection.commit();
		} catch (SQLException e) {
			System.err.println("Failed initializing database");
			e.printStackTrace();
		}
		
		PreparedStatement insertCmd = createStatement(SURVEY_INSERT);
		while (insertCount < recordCount) {
			try {
				setStatementValues(insertCmd);
				insertCmd.addBatch();
				insertCount++;
				if (insertCount % 1000 == 0) {
					insertCmd.executeBatch();
					_connection.commit();
				}
				if (_debugFlag && insertCount % 10000 == 0)
					System.err.print(".");
			} catch (SQLException e) {
				throw new RuntimeException("Failed Load", e);
			}
		}
		System.out.println(String.format(
				"Load complete. %d records loaded at a rate of %d rows/sec",
				insertCount, (insertCount * 1000) / (System.currentTimeMillis() - startLoad)));
	}
	
	/**
	 * Return a prepared statement for the thread's local connection.
	 * @param staPE_2site-1tementStr Any SQL statement, including ? params as needed.
	 * @return
	 */
	public PreparedStatement createStatement( String statementStr ) {
		PreparedStatement stmt = null;
		
		try {
			stmt = _connection.prepareStatement(statementStr);
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
	public void setStatementValues ( PreparedStatement stmt ) throws SQLException {
		stmt.setString(1, randText(45));
		stmt.setString(2, randText(45));
		stmt.setString(3, randText(1000));
		stmt.setInt(4, _randGen.nextInt(1999999999));
	}
	
	public static final char[] chars = {'a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z','1','2','3','4','5','6','7','8','9','0','.','-','_','=','$','@','#'};
	public String randText( int size ) {
	    char[] text = new char[size];
	    for (int i = 0; i < size; i++)
	    {
	        text[i] = chars[_randGen.nextInt(chars.length)];
	    }
	    return new String(text);
	}

	/**
	 * Benchmark driver for each client thread.
	 * Threads used the shared runFlag to synchronize start and stop.  The client repeatedly invokes a 
	 * prepared statement for a table INSERT, committing after each insert. Each client thread generates
	 * unique random data for the contents of the insert, and tracks successful and failed transactions.
	 */
	public void run() {
		_connection = connection(_connectURL);
		while (true) {
			synchronized(_runFlag) {
				try { _runFlag.wait(1000); }
				catch (InterruptedException e) { ; }
			}
			if (_runFlag.get())
				break;
		}		
		PreparedStatement stmt = null;
		long errorCount = 0;
		try {
			while (_runFlag.get() && errorCount < MAX_ERROR_COUNT) {
				if (stmt == null)
					stmt = createStatement(SURVEY_INSERT);
				try {
					setStatementValues(stmt);
					int updateCount = stmt.executeUpdate();
					if (updateCount == 1) {
						_txnCount++;
						if (_insertsPerCommit != 0 && _txnCount % _insertsPerCommit == 0)
							_connection.commit();
					} else {
						_errCount++;
						_connection.rollback();
					}
				} catch (SQLException e) {
					_errCount++;
					_connection.rollback();
					if (debug)
						System.err.print("!");
					stmt.close();
					if (_errCount % 100 == 0) {
						_connection.close();
						_connection = connection(_connectURL);
					}
				}
			}
		} catch (Exception e) {
			System.err.println("\n**** Fatal Error"+e.getMessage());
			e.printStackTrace();
		} finally {
			try { stmt.close();} catch (Exception e) { ; }
			try {_connection.close();} catch (Exception e) { ; }
		}
		if (this._debugFlag)
			System.err.println("Client finished.");
	}

	static final long MAX_ERROR_COUNT = 10000;
	static final long MAX_TIME_SECS = 3600;

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
	static int    port = 3307;
	static String dbname = "QuickMobile";
	static String user = "root";
	static String password = "password";
	static boolean debug = false;
	static boolean load = false;
	static int    numclients = 10;
	static int    loadcount = 100000;
	static int    insertcount = 100000;
	static int    insertsPerCommit = 1;


	public static final String HELP_TEXT = "SQL Insert Benchmark example, version " + VERSION +
			"Syntax:  java com.parelastic.bench.test.Example [-help] [-debug] [-load] [param=value] ... " +
			"\nWhere param can be:" +
			"\n\thost - default: " + host +
			"\n\tport - default: " + port +
			"\n\tdbname - default: " + dbname +
			"\n\tuser - default: " + user +
			"\n\tpassword - default: " + password +
			"\n\tnumclients - default: " + numclients +
			"\n\tloadcount - default: " + loadcount +
			"\n\tinsertcount - default: " + insertcount +
			"\n\tinsertsPerCommit - default: " + insertsPerCommit +
			"\nIf -load is specified, the program (re)loads the Database and exits." +
			"\nIf insertsPerCommit is zero, auto-commit is enabled.";
	
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
				} else if (arg.equalsIgnoreCase("-load")) {
					load = true;
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
				} else if (w[0].equalsIgnoreCase("numclients")) {
					numclients = Integer.parseInt(w[1]);
				} else if (w[0].equalsIgnoreCase("loadcount")) {
					loadcount = Integer.parseInt(w[1]);
				} else if (w[0].equalsIgnoreCase("insertcount")) {
					insertcount = Integer.parseInt(w[1]);
				} else if (w[0].equalsIgnoreCase("insertsPerCommit")) {
					insertsPerCommit = Integer.parseInt(w[1]);
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
	 * Load the database and run the benchmark.
	 * This is a multi-threaded SQL benchmark.  The __runFlag__ object is used to gate the starting and stopping of client threads.
	 * Initially, the database is pre-loaded with INITIAL_DB_SIZE records, to ensure we get a reliable, steady-state measure free
	 * of any initial creation and index contention issues. Then the client threads are launched and notified to start executing
	 * the INSERT transactions.  In the run phase, the clients commit after each individual insert, which is slower than a batch
	 * operation would be, but more flexible and less prone to lock contention.
	 * During execution, the program periodically prints the current TPS rate and the accumulated number of errors.
	 * Upon completion, a brief report is printed summarizing transaction and error counts, along with overall transactions per second.
	 * @param args [-help] [-debug] [host=VAL] [port=VAL] [dbname=VAL] [user=VAL] [password=VAL]
	 */
	public static void main(String[] args) {
		AtomicBoolean __runFlag__ = new AtomicBoolean(false);
		parseCommandArgs(args);
		String connectUrl = String.format("jdbc:mysql://%s:%d/%s", host, port, dbname );
		if (load) {
			int status = 0;
			try {
				System.err.println("Initializing database.");
				loadDriver();
				Example loader = new Example(__runFlag__, "Loader-Main", debug, connectUrl, insertsPerCommit);
				loader.initializeDatabase(loadcount);
			}
			catch (RuntimeException e) {
				System.err.println("\n**** Fatal Error" + e.getLocalizedMessage());
				e.printStackTrace();
				status = -1;
			} finally {
				System.exit(status);
			}
		}
		
		Example[] client = new Example[numclients];
		ThreadGroup allThreads = new ThreadGroup("AllThreads");
		System.err.println("\nStarting client test threads.");
		synchronized (__runFlag__) {  // block run flag until all threads are waiting
			for (int threadNum=0; threadNum < numclients; threadNum++) {
				String threadID = String.format("Client-%d", threadNum);
				client[threadNum] = new Example(__runFlag__, threadID, debug, connectUrl, insertsPerCommit);
		        Thread t = new Thread(allThreads, client[threadNum]);
		        t.start();
			}
		}
		__runFlag__.set(true);
		try {
			Thread.sleep(5000);  // Ensure all clients have had time to connect
		} catch (InterruptedException e) { ; }
		synchronized (__runFlag__) {
			__runFlag__.notifyAll(); // Activate client threads
		}
		
		int period = 0;
		long startTime = System.currentTimeMillis();
		long totalTxns=0;
		long totalErrs = 0;
		System.out.println("\nPeriod\tTxn/sec\t#Txns\t#Errors");
		try {
			while (totalTxns < insertcount) {
				try {
					Thread.sleep(10000);
				} catch (InterruptedException e) {
					;
				}
				long currTxns = 0;
				long currErrs = 0;
				for (int i = 0; i < client.length; i++) {
					currTxns += client[i].txnCount();
					currErrs += client[i].errCount();
				}
				System.out.println(String.format("%s\t%s\t%s\t%s",
						period,
						(currTxns - totalTxns) / 10,
						currTxns,
						currErrs));
				totalTxns = currTxns;
				totalErrs = currErrs;			period++;
				if (totalTxns >= insertcount
						|| totalErrs >= MAX_ERROR_COUNT
						|| (System.currentTimeMillis()) / 1000 - startTime > MAX_TIME_SECS) {
					break;
				}
			}
		} catch (Exception e) {
			System.err.println("FATAL: " + e.getLocalizedMessage());
		}

		__runFlag__.set(false); // tell client threads to stop
		
		long totalTime = (System.currentTimeMillis() - startTime) / 1000;
		System.out.println(String.format("\n\nBenchmark complete.\nTotal Time:\t%s\tseconds\nTransactionCount:\t%s\nTxnRate:\t%s\ttxn/sec\nErrorCount:\t%s",
											totalTime, totalTxns, totalTxns/totalTime, totalErrs));

		System.err.println("Cleaning up (press Ctl-C to abort).");
		allThreads.interrupt();
		
		while (allThreads.activeCount() > 0) {
			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) { ; }
			System.err.print(".");
			if ((System.currentTimeMillis() - startTime)/1000 > 100) {
				System.err.println("Timed out waiting for thread shutdown");
				System.exit(-100);
			}
		}
		System.err.println("\nBenchmark complete.");
	}

}
