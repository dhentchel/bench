/* Client.java - Copyright (c) 2014, David Paul Hentchel
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

import bench.TestContext.TestStats;

/**
 * Run test threads to model individual SQL client connections.
 * This module manages the actual transaction logic and txn performance counters.
 * @author dhentchel
 *
 */
public class Client implements Runnable {
	private static final long MAX_ERROR_COUNT = 3;
	TestContext _ctx;
	DatabaseManager _dbmgr;
	Session _session;
	Long _clientID;
	ClientManager _manager;

	public Client( TestContext ctx, long clientID, ClientManager manager ) {
		_ctx = ctx;
		_clientID = clientID;
		if (manager == null)
			_manager = new SimpleClientManager();
		else
			_manager = manager;
		_dbmgr = new DatabaseManager(_ctx);
		_session = new Session(_dbmgr, _clientID);	
		_manager.setState(_clientID, ClientState.NEW);
	}

	public TestStats getStats() { return new TestStats(_session); }
	
	/**
	 * Perform initial schema creation and database load.
	 */
	public void load ( ) {
		_manager.setState(_clientID, ClientState.LOADING);
		Script loadScript = Script.launchScript(_ctx.getString("scriptclass"), _ctx.getString("loadscriptfile"), _clientID, _ctx.getString("loadscriptvars"));
		boolean processing = true;
		while (processing) {
			try {
				String line = loadScript.nextLine();
				if (line == null)
					processing = false;
				else
					processing = _session.execute(line);
			} catch (Exception e) {
				processing = false;
				throw new RuntimeException("Script Fatal", e);
			}
		}
		if (TestContext.isVerbose)
			System.err.println(getStats().show());
		System.err.println("Client: load complete.");
	}
	/**
	 * Benchmark driver for each client thread.
	 * Threads used the shared runFlag to synchronize start and stop.  The client repeatedly invokes a 
	 * prepared statement for a table INSERT, committing after each insert. Each client thread generates
	 * unique random data for the contents of the insert, and tracks successful and failed transactions.
	 */
	public void run() {
		Script runScript = Script.launchScript(_ctx.getString("scriptclass"), _ctx.getString("runscriptfile"), _clientID, _ctx.getString("runscriptvars"));
		_manager.setState(_clientID, ClientState.READY);
		while (true) {
			synchronized(_manager) {
				try { _manager.wait(1000); }
				catch (InterruptedException e) { ; }
			}
			if (_manager.running())
				break;
		}
		long fatalCount = 0;
		Exception finalException = null;
		boolean sessionIsActive = true;
		try {
			_manager.setState(_clientID, ClientState.RUNNING);
			while (sessionIsActive && _manager.running() && fatalCount < MAX_ERROR_COUNT) {
				try {
					String line = runScript.nextLine();
					if (line == null)
						sessionIsActive = false;
					else
						sessionIsActive = _session.execute(line);
				} catch (Exception e) {
					fatalCount++;
					finalException = e;
					e.printStackTrace(System.err);
				}
			}				
		} catch (Exception e) {
			System.err.println("\n**** Fatal Error"+e.getMessage());
			finalException = e;
			e.printStackTrace();
		} finally {
			_session.endSession();
			sessionIsActive = false;
			if (finalException != null) {
				_manager.setState(_clientID, ClientState.ABORTED);
				throw new RuntimeException("Client: Fatal exception limit exceeded.", finalException);
			} else {
				_manager.setState(_clientID, ClientState.DONE);
			
			}
		}
		if (TestContext.isVerbose)
			if (TestContext.isVerbose) System.err.println(String.format("Client %d finished.", _clientID));
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		TestContext context = new TestContext();
		context.put("loadscriptfile", "test/Client_test_script.gen");
		context.put("loadscriptvars", "none");
		context.parse(args);
		
		Client client = new Client(context, 0, null);
		client.load();
	}

	/**
	 * The client state is set by the client thread to communicate its state to the ClientManager.
	 * Client states have the following general meanings:
	 * <ol><li>NONE - optional empty state before the client is instantiated</li>
	 * <li>NEW - Client has instantiated itself, including a successful database connection</li>
	 * <li>LOADING - Client is in the process of loading the database</li>
	 * <li>READY - Client thread is ready to begin a test run</li>
	 * <li>RUNNING - Test run is in progress</li>
	 * <li>ABORTED - Client has aborted and will clean up</li>
	 * <li>DONE - Test run or abort processing is finished the the client thread can be destroyed</li>
	 * </ol>
	 * @author dhentchel
	 *
	 */
	public enum ClientState {
		NONE,
		NEW,
		LOADING,
		READY,
		RUNNING,
		ABORTED,
		DONE;
	}

	/**
	 * Communicate the Client's state to a driver program.
	 * Pass an instance of this to the constructor if you want the client to update you regarding state changes.
	 * The implementor is responsible for ensuring thread safety.
	 */
	public static interface ClientManager {
		public boolean running();
		public void setState ( long clientID, ClientState state);
	}
	
	public static class SimpleClientManager implements ClientManager {
		private ClientState _state = ClientState.NEW;
		
		@Override
		public boolean running() {
			if (_state == ClientState.RUNNING)
				return true;
			else
				return false;
		}

		@Override
		public void setState(long clientID, ClientState state) {
			_state = state;
		}
		
	}
}
