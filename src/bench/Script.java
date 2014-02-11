/* Script.java - Copyright (c) 2014, David Paul Hentchel
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

import bench.gen.GenFile;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

/**
 * Pluggable interface to generate SQL commands.
 * This abstract class provides a mechanism for pluggable components that generate SQL syntax.
 * Commands can include readonly ("SELECT"), update ("INSERT", "UPDATE"), DDL ("CREATE"), DML
 * ("COMMIT", "PREPARE") and metadata ("SET", "SHOW") commands.  It is the responsibility of the
 * caller to add any logic not explicitly sent as script commands.
 * <pre>Examples of commands you may want to pre-pend in startup scripts:
 *   - SET GLOBAL AUTOCOMMIT=1;
 *   - SET GLOBAL cachePrepStmts=true;
 *   - USE MyDBName;</pre>
 * <pre>Prepared statements are generated using extended MySQL statement syntax:
 *   - PREPARE stmt1 FROM ...;  Creates Prepared Statement from SQL.
 *   - EXECUTE stmt1 USING var1,  var2; Sets parameters and executes the prepared statement.
 *   - DEALLOCATE PREPARE stmt1;  Destroys the prepared statement and related resources.
 *   Note that unlike standard MySQL you provide argument values directly in the execute statement,
 *   rather than referencing set SQL variables; the command processor will attempt to perform the
 *   appropriate data conversion to instantiate the request.</pre>
 * <pre>Scripts are intended to support multiple client processes on different hosts, and an arbitrary
 * number of threads within the client process.  To ensure uniqueness and sequence constraints, it is
 * advised that you manage scripts as follows:
 * 1. explicitly assign a unique integer clientid for each thread across all concurrent driver processes.
 * 2. use the launchScript Factory class, which will launch a new Script instance in its own thread.
 * 3. iterate the returned BufferedReader via its readLine() method.
 * 4. Call the destroy() method upon completion to close the background thread and clean up buffers.
 * @author dhentchel
 *
 */
public abstract class Script implements Runnable {
	protected long _clientID = 0;

	abstract protected void initialize ( String scriptFile, long clientID, String params);
	
	abstract public String nextLine ();
	
	abstract public void destroy ( );

	@Override
	public void run() {
		// TODO Auto-generated method stub
		
	}

	public static Script launchScript ( String className, String scriptFile, long clientID, String params ) {
		Script script;
		try {
			script = (Script) Class.forName(className).newInstance();
		} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
			throw new RuntimeException("Cannot instantiate Script class subtype "+className, e);
		}
		if (TestContext.isVerbose)
			System.err.println(String.format("Script: Initialize script for client %d with scriptFile %s, and parameters: %s", clientID, scriptFile, params));
		script.initialize(scriptFile, clientID, params);
		return script;
	}

	/**
	 * Simple test program.
	 * TODO: convert to junit test.
	 * @param args
	 */
	public static void main(String[] args) {
		long clientID = 7;
		String params = String.format("{clientid=%s}", String.valueOf(clientID));
		BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
		boolean keepGoing = true;
		Script script = null;
		
		script = Script.launchScript("com.parelastic.bench.FileScript", "test/Product_Queries.gen", clientID, params);
		System.out.println("Printing output of FileScript. After each line, hit <Enter> to continue, 'Q' to quit" +
				"");
		while (keepGoing) {
			try {
				String line = script.nextLine();
				System.out.println(line);
				System.out.print(" > ");
				String answer = stdin.readLine();
				if (answer.length() > 0 && answer.substring(0, 1).equalsIgnoreCase("q"))
					keepGoing = false;
			} catch (Exception e) {
				keepGoing = false;
				throw new RuntimeException("Script Fatal", e);
			}
		}
		script.destroy();
		
		keepGoing = true;
		script = Script.launchScript("com.parelastic.bench.GenScript", "test/Product_Queries.gen", clientID, params);
		System.out.println("Printing output of GenScript. After each line, hit <Enter> to continue, 'Q' to quit");
		while (keepGoing) {
			try {
				String line = script.nextLine();
				System.out.println(line);
				System.out.print(" > ");
				String answer = stdin.readLine();
				if (answer.length() > 0 && answer.substring(0, 1).equalsIgnoreCase("q"))
					keepGoing = false;
			} catch (Exception e) {
				keepGoing = false;
			}
		}
		script.destroy();
		
		System.out.println("Test complete.");
	}

}

/**
 * Concrete Script implementation using the bench gen text generation facility.
 * @author dhentchel
 *
 */
class GenScript extends Script {
	static final int BUFFER_SIZE = 2048;
	BufferedReader _scriptReader = null;
	Thread _backgroundWriteThread = null;
	
	@Override
	protected void initialize(final String scriptFile, final long threadID, final String params) {

		try {
			PipedInputStream genStream = new PipedInputStream(BUFFER_SIZE);
			final PipedOutputStream out = new PipedOutputStream(genStream);
			_backgroundWriteThread = new Thread(new Runnable() {
				@Override
				public void run() {
					GenFile _scriptGenerator = new GenFile();
					_scriptGenerator.parseFile(scriptFile);
					_scriptGenerator.setVariables(params);
					_scriptGenerator.generate(threadID, out);
				}
			});
			_scriptReader = new BufferedReader(new InputStreamReader(genStream));
			_backgroundWriteThread.start();
		} catch (Exception e) {
			throw new RuntimeException("ScriptGen cannot launch new thread instance", e);
		}
	}

	/**
	 * Returned a line of buffered output.
	 * This runs in the client thread, reading the piped input stream populated by the output stream of the background thread.
	 */
	@Override
	public String nextLine() {
		try {
			return _scriptReader.readLine();
		} catch (IOException e) {
			return null;
//			throw new RuntimeException("Failure retrieving script command", e);
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	public void destroy() {
		_backgroundWriteThread.interrupt();
		if (_backgroundWriteThread.isAlive()) {
			System.err.println("ScriptGen:  WARNING: background script thread didn't terminate - forcing abort");
			_backgroundWriteThread.stop();
		}
	}

	
}

/**
 * Concrete Script implementation as a simple input file.
 * @author dhentchel
 *
 */
class FileScript extends Script {
	BufferedReader _scriptReader = null;
	
	@Override
	protected void initialize(String scriptFile, long clientID, String params) {
		try {
			_scriptReader = new BufferedReader(new FileReader(scriptFile));
		} catch (FileNotFoundException e) {
			throw new RuntimeException("FileScript: Failure initializing script", e);
		}
	}
	
	@Override
	public String nextLine() {
		try {
			return _scriptReader.readLine();
		} catch (IOException e) {
			throw new RuntimeException("Failure retrieving script command", e);
		}
	}

	@Override
	public void destroy() {
		try {
			_scriptReader.close();
		} catch (Exception e) {
			System.err.println("ScriptFile: WARNING: Error closing script file.");
		}		
	}

}
