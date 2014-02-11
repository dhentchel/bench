/* Driver.java - Copyright (c) 2014, David Paul Hentchel
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

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import bench.Client.ClientState;
import bench.TestContext.TestStats;

/**
 * Run and Manage multi-client SQL benchmark tests. This class handles the complete distributed
 * benchmark test. Actual running of benchmark clients is done by the Client
 * class, and all DBMS and SQL management functions are in the Database class.
 * One running instance of this class is launched on each host running benchmark clients. 
 * TODO Orchestrate multiple Drivers in a run
 * 
 * @author dhentchel
 */
public class Driver implements Runnable, Client.ClientManager {
	static final long MAX_ERROR_COUNT = 10000;
	static final long MAX_STAGING_WAIT_MSECS = 30000;
	static final long WARMUP_TIME_SECS = 10; // Time allowed for sessions to ramp up
	static final long MAX_TIME_SECS = 3600;
	static final long MAX_IGNORED_CLIENTS = 1; // If this many clients are done or aborted, the test is terminated early
	static final String VERSION = "1.2";
	
	private TestContext _context;
	private int _numclients;
	private HashMap<Long, Client> _client;
	
	/**
	 * Thread synchronization fields and methods.
	 */
	private AtomicBoolean _runFlag;
	private HashMap<Long, ClientState> _clientState;
	private AtomicInteger _clientReadyCount = new AtomicInteger(0);
	private AtomicInteger _clientRunningCount = new AtomicInteger(0);
	private AtomicInteger _clientFinishedCount = new AtomicInteger(0);
	private ThreadGroup _allThreads;

	private long _startTime;
	private long _endTime;
	private TestStats _priorStats;

	public Driver ( TestContext ctx ) {
		_context = ctx;
		_numclients = _context.getInt("numclients");
		_client = new HashMap<Long, Client>();
		_clientState = new HashMap<Long, ClientState>();
	}

	public void exec_load ( ) {
		System.err.println("Driver: Running load.");
		Client loader = new Client(_context, 0, null);
		loader.load();
	}

	public void exec_launch_clients( ) {
		
		System.err.println("Driver: Launching client threads.");
		if (_context.getEnumIndex("starter") == 1) {
			System.err.println("Driver: Ready to start Client execution. Press <enter> key to continue:");
			try {
				System.in.read();
			} catch (IOException e) { ; }
		}

		_allThreads = new ThreadGroup("AllThreads");
		_runFlag.set(false);
		System.err.println("Starting client test threads.");
		for (int threadNum=0; threadNum < _numclients; threadNum++) {
			long clientID = computeClientID(_context, threadNum);
			Client client = new Client(_context, threadNum, this);
			_client.put(clientID, client);
			_clientState.put(clientID, ClientState.NEW);
	        Thread t = new Thread(_allThreads, client);
	        t.start();
		}
		synchronized (_clientReadyCount) {  // now wait for the client ready count to reach criterion (100%)
			if (_clientReadyCount.get() < _numclients) {
				try {
					_clientReadyCount.wait(MAX_STAGING_WAIT_MSECS);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		_runFlag.set(true);
		synchronized (this) {
			this.notifyAll(); // Activate client threads
		}
		try {Thread.sleep(WARMUP_TIME_SECS);} catch (InterruptedException e) {	; }
		if (_clientReadyCount.get() > 0)
			throw new RuntimeException("Driver FATAL: Client threads still in Ready state after warmup period");
	}

	public void exec_monitor_clients ( ) {
		System.err.println("Driver: Monitoring threads.");
		int period = 0;
		_startTime = System.currentTimeMillis();
		_priorStats = new TestStats();
		long intervalSecs = _context.getInt("rptintervalsecs");
		int numIntervals =  _context.getInt("numrptintervals");
		System.out.println("\nPeriod\tOps/sec\tReads\tWrites\t#Errs");
		try {
			while (running()) {
				synchronized (_clientFinishedCount) {
					if (_clientFinishedCount.get() > MAX_IGNORED_CLIENTS) {
						_runFlag.set(false);
						System.err.println("Driver:  Terminating early because too many clients ended or aborted before all cycles completed.");
					} else {
						_clientState.wait(intervalSecs);
					}
				}

				if (period > 0 && !running()) // Premature termination; only print stats
					break;
				
				TestStats newStats = new TestStats();
				for (Client client : _client.values()) {
					newStats.tally(client.getStats());
				}
				System.out.println(String.format("%s\t%s\t%s\t%s\t%s",
						period,
						(newStats.statementCount - _priorStats.statementCount) / intervalSecs,
						newStats.queryCount,
						newStats.updateCount,
						newStats.errorCount));
				_priorStats = newStats;
				period++;
				if (newStats.errorCount >= MAX_ERROR_COUNT
						|| (System.currentTimeMillis()) - _startTime > MAX_TIME_SECS * 1000) {
					System.err.println("Driver: FATAL - error count or max time exceeded, terminating test.");
					_runFlag.set(false);
					break;
				}
				if (period == numIntervals) 
					_runFlag.set(false);
			}
		} catch (Exception e) {
			System.err.println("FATAL: " + e.getLocalizedMessage());
		}
	}

	public void exec_terminate ( ) {
		System.err.println("Driver: Waiting for threads to complete.");
		_runFlag.set(false); // tell client threads to stop
		_endTime = System.currentTimeMillis();
		try {Thread.sleep(10000);} catch (InterruptedException e) {	; }
		
		System.err.println("Driver: Cleaning up (press Ctl-C to abort).");
		_allThreads.interrupt();
		while (_allThreads.activeCount() > 0) {
			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) { ; }
			if (TestContext.isVerbose) System.err.print(".");
			if ((System.currentTimeMillis() - _startTime)/1000 > 100) {
				System.err.println("Timed out waiting for thread shutdown");
				System.exit(-100);
			}
		}
	}
	
	public void exec_print_stats ( ) {
		long totalTime = (_endTime - _startTime) / 1000;
		long totalTxns = _priorStats.updateCount + _priorStats.queryCount;
		System.err.println("Driver: Printing final stats.");
		System.out.println(String.format("\n\nBenchmark complete.\nTotal Time:\t%s\tseconds\nTotal Ops:\t%s\nTxnRate:\t%s\ttxn/sec\nErrorCount:\t%s",
											totalTime, totalTxns, totalTxns/totalTime, _priorStats.errorCount));
	}
	
	public static long MAX_CLIENT_THREADS = 100;
	/**
	 * Determine a canonical, unique Client ID for a given Client thread.
	 * This combines the driverID number and threadID number to give a unique long value.
	 */
	public static long computeClientID ( TestContext ctx, long threadID ) {
		if (threadID >= MAX_CLIENT_THREADS)
			throw new RuntimeException(String.format("Fatal: Cannot run Driver instance with more than %s clients.", MAX_CLIENT_THREADS));
		long driverID = ctx.getInt("driverid");
		return (driverID * MAX_CLIENT_THREADS + threadID);
	}

	/**
	 * A singleton Driver thread may be run to monitor status and orchestrate Clients.
	 * TODO move client run monitoring from main() to here.
	 */
	@Override
	public void run() {
		;
	}

	@Override
	public void setState(long clientID, ClientState state) {
		synchronized (_clientState) {
			ClientState oldState = _clientState.get(clientID);
			if (oldState == ClientState.NEW) {
				if (state == ClientState.READY) {
					long newReadyCount = _clientReadyCount.incrementAndGet();
					if (newReadyCount >= _numclients) // trigger when all clients are ready
						synchronized(_clientReadyCount) {
							_clientReadyCount.notify();
						}
				} else					
					throw new RuntimeException(String.format("Driver: Invalid state transition: %s to %s", oldState, state));
			} else if (oldState == ClientState.READY) {
				if (state == ClientState.RUNNING) {
					_clientReadyCount.decrementAndGet();
					_clientRunningCount.incrementAndGet();
				} else if (state == ClientState.ABORTED) {
					_clientReadyCount.decrementAndGet();
					long newFinishedCount = _clientFinishedCount.incrementAndGet();
					if (newFinishedCount >= MAX_IGNORED_CLIENTS) // trigger premature termination if threshold for finished clients is hit
						synchronized(_clientFinishedCount) {
							_clientFinishedCount.notify();
						}
				} else
					throw new RuntimeException(String.format("Driver: Invalid state transition: %s to %s", oldState, state));
			} else if (oldState == ClientState.RUNNING) {
				if (state == ClientState.DONE || state == ClientState.ABORTED) {
					_clientRunningCount.decrementAndGet();
					long newFinishedCount = _clientFinishedCount.incrementAndGet();
					if (newFinishedCount > MAX_IGNORED_CLIENTS) // trigger premature termination if threshold for finished clients is hit
						synchronized(_clientFinishedCount) {
							_clientFinishedCount.notify();
						}
				}
			} else
				throw new RuntimeException(String.format("Driver: Invalid state transition: %s to %s", oldState, state));
				
		}
	}

	@Override
	public boolean running() {
		return _runFlag.get();
		}

	/**
	 * Load the database and run the benchmark.
	 * This is a multi-threaded SQL benchmark.  The __runFlag__ object is used to gate the starting and stopping of client threads.
	 * If the 'doload' flag is set, the database is pre-loaded, using the loadscriptfile; this script should recreate and load tables to ensure that
	 * the database is returned to a known state. Then the client threads are launched and notified to start executing
	 * SQL transactions.  In the run phase, the clients execute the configured runscriptfile script.
	 * During execution, the program periodically prints the current TPS rate and the accumulated number of errors.
	 * Upon completion, a brief report is printed summarizing transaction and error counts, along with overall transactions per second.
	 * @param args [-help] [-debug] [host=VAL] [port=VAL] [dbname=VAL] [user=VAL] [password=VAL]
	 */
	public static void main(String[] args) {
		System.err.println("Driver: Setting up test context.");
		TestContext context = new TestContext();
		context.parse(args);
		if (TestContext.isVerbose) context.writeConfig(System.err);
		Driver driver = new Driver(context);
		
		if (context.getBool("doload")) {
			driver.exec_load();
		}
		
		driver.exec_launch_clients();
		
		driver.exec_monitor_clients();

		driver.exec_terminate();
	
		driver.exec_print_stats();
		
		System.err.println("\nBenchmark complete.");
	}

}
