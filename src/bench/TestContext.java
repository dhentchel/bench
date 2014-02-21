/* TestContext.java - Copyright (c) 2014, David Paul Hentchel
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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.Vector;

/**
 * Extends Properties to parse and manage runtime parameters for test programs.
 * The TestParams class provides greater control over the input parameters that characterize a test. 
 * While the actual name value pairs for parameters are always stored as Strings in the parent Properties
 * object, TestParams adds additional management and validation. For example, it provides a mechanism
 * for statically declaring known parameters, along with descriptive information, default values and some
 * primitive type information. The application can retrieve parameter values as simple properties, using
 * the Properties.getProperty(String) method, or can use accessor functions (getInt, getBool, getString) to cast
 * return values to a specific Java type.  The embedded TestParam class is a wrapper holding this metadata
 * for a particular parameter type.
 * <p>Parameters can be entered and parsed in a variety of ways:
 * <ol>
 * <li>Default values (defined in TestParam static initializer)</li>
 * <li>Command line argument array (type String[])</li>
 * <li>System properties.</li>
 * <li>Properties file (default filename params.properties)</li>
 * <li>Explicit reset of property via TestParams.set method)</li>
 * <li>Interactive prompt (if -prompt specified in cmd line args</li>
 * </ol></p>
 * <p>To add a new parameter type at runtime, you simply include it on the command line as a name/value
 * pair, e.g.:
 * <pre><br><t>java bench.Driver -props in.prop myParam=4,otherParam=England<br></pre>
 * Programatically, you add new parameters by invoking one of the static TestParam.add methods. Note that
 * the way parameters are displayed in printouts and prompts will correspond to the sequence in which they
 * are created, and these displays will be broken into categories based on the category string names entered
 * via the TestParam.addCategory method. All the built-in parameters are added via a static initializer in
 * this class using those methods.
 * </p>
 * <p>The validate() method performs some specific validation checks on parameter values, based on data type
 * and other constraints passed in when you added the param.
 * </p>
 * @author davehentchel@gmail.com
 * @version 1.0
 */
public class TestContext
extends Properties
{
	public static final long serialVersionUID = 487075001;
	/** Static field for log output; set 'true' for verbose log messages. */
	public static boolean isVerbose = false;  // set using -verbose command line option
	/** Console access allowed; if false, no direct System.in or System.out operations will be performed. */
	public static boolean isConsole = true;  // set false using -headless command line option
	private BufferedReader sysin = new BufferedReader(new InputStreamReader(System.in)); // for convenience declare this once, here.
	private String propFile = "driver.properties";
	private boolean isInteractive = false;
	
	/**
	 * Set up hashmap with initial values for all params.
	 * Note that the static TestParam collections are already built. This constructor goes through
	 * the standard driver params in the list and sets the default value.  If a system property
	 * of the same name exists, it overrides the compile-time default.
	 */
	public TestContext ( )
	{
		super();
		// set up default values for Standard params
		for (Iterator<TestParam> itr = TestParam.list(false); itr.hasNext();)
		{
			TestParam param = (TestParam) itr.next();
			if (param.paramType == TestParam.STANDARD_PARAM)
				this.put(param.paramName, System.getProperty(param.paramName, param.defaultVal)); // allow global env var to override
		}
	}
	
	public static final String INT_SCALARS = "kmg";

	/** Return a parameter as an int.
	 * <p>If the final character of the string is 'k', 'm' or 'g', it indicates that the scale is in kilo-,
	 *  mega-, or giga- units (i.e. 1024,1024*1024 or 1024*1024*1024).</p> 
	 * <p>If the current value of the input property is not a valid integer, this routine will attempt
	 * to find a default value for that property in the standard list of params. If that doesn't work
	 * it simply returns '1'.`</p>
	 * @param propName Lookup key for a property that represents an integer.`
	 */
	public int getInt ( String propName )
	{
		int value = 1;
		String propVal = getProperty(propName.toLowerCase()).trim();
		if (propVal == null || propVal.length() == 0)
		{
			System.err.println("TestContext: Numeric property not found: " + propName);
			return value;
		}
		
		try
		{
			value = getIntValue(propVal);
		}
		catch (NumberFormatException e) // fall back to default value for the property
		{
			TestParam param = TestParam.get(propName);
			if (param.dataType == TestParam.NUMBER_DATATYPE)
			{
				try { value = getIntValue(param.defaultVal); }
				catch (NumberFormatException nfe) { throw new RuntimeException("Illegal numeric parameter default for " + propName); }
			}
			else
				System.err.println("TestContext: Not a numeric parameter: " + propName);
		}
		return value;
	}
	
	/** Return a parameter as an int.
	 * Currently, we don't check here whether type is supposed to be boolean, just default to
	 * false if we can't find a valid value.
	 */
	public boolean getBool ( String propName )
	{
		String propVal = getProperty(propName.toLowerCase());
		if (propVal == null)
		{
			System.err.println("TestContext: Boolean property not found: " + propName);
			return false;
		}
		if (propVal.equalsIgnoreCase("true"))
			return true;
		else if (propVal.equalsIgnoreCase("false"))
			return false;
		else
		{
			System.err.println("TestContext: Error:" + propName + " contains illegal boolean property: " + propVal + " - defaulting to false");
			return false;
		}
	}
	
	/** Return a parameter as a non-null String.
	 * Take the Property string value, trim off leading and trailing whitespace, and convert null values
	 * to empty strings.
	 */
	public String getString ( String propName )
	{
        String propVal = ""; 
        if (propName != null) 
        { 
                propVal = getProperty(propName.toLowerCase().trim()); 
                if (propVal == null) 
                        propVal = ""; 
        } 
        return propVal.trim(); 
	}
	/** Return the enumeration index of the param as an int.
	 * <p>The param type must be "enum" or an exception is raised.  For enum types, a
	 * String array of the matching values is maintained and this routine simply
	 * returns the index within that array, or -1 if the value is not found.</p> 
	 * <p>NOTE:  Unlike param names, enum values are case-sensitive, so a warning
	 * message is printed if the match would have occurred on a case-insensitive search.
	 * </p>
	 * @param propName Lookup key for a property that represents an enum.
	 * @return Index value if string found in enum validation list, -1 otherwise.
	 */
	public int getEnumIndex ( String propName )
	{
		String propVal = getProperty(propName.toLowerCase()).trim();
		if (propVal == null || propVal.length() == 0) {
			System.err.println("TestContext: Property not found: " + propName);
			return -1;
		}
		TestParam param = TestParam.get(propName);
		if (!(param.dataType == TestParam.ENUM_DATATYPE)) {
			System.err.println("TestContext: Cannot return Enum Index, property is not type 'enum': " + propName);
			return -1;
		}
		int indx = param.lookupEnumIndex(propVal);
		if (indx >= 0)
			return indx;
		else
			return -1;
	}
	
	/** Parse command args, perform help or prompt functions if specified.
	 * @param argv Command argument string, compared against TestParams.
	 */
	public boolean parse ( String[] argv )
	{
		boolean showHelp = false;
		ArrayList<String> userParms = new ArrayList<String>(); 
		// Check parameters and set appropriate variables
		for (int i = 0; i < argv.length; i++)
		{
			String arg = argv[i];
			if (arg.startsWith("-"))
			{
				if (arg.equalsIgnoreCase("-help"))
				{
					showHelp = true;
				}
				else if (arg.equalsIgnoreCase("-prompt"))
				{
					isInteractive = true;
				}
				else if (arg.equalsIgnoreCase("-props"))
				{
					i++;
					propFile = argv[i];
					try 
					{
						String files[] = propFile.split(",");
						for (int j = 0; j<files.length; j++) {
							load(new FileInputStream(files[j]));
						}
						Enumeration<?> keys = this.propertyNames();  // now coerce all property names to lower case...
						while (keys.hasMoreElements())
						{
							String key = (String) keys.nextElement();
							if (!TestParam.exists(key))  // add any User properties to static collections
								userParms.add(key);
							else if (!key.equals(key.toLowerCase()))  // coerce system property names to lower case
							{
								String val = this.getProperty(key);
								this.remove(key);
								this.setProperty(key.toLowerCase(), val);
							}
						}
					}
					catch (Exception e) { System.err.println("TestContext: Missing properties file, ignored: " + propFile); }
				}
				else if (arg.equalsIgnoreCase("-verbose"))
				{
					isVerbose = true;
				}
				else if (arg.equalsIgnoreCase("-headless"))
				{
					isConsole = false;
				}
				else
				{
					System.err.println ("TestContext: Error: unexpected argument - " + arg);
					usage(false);
					return false;
				}
			}
			else if (arg.indexOf("=")<= 0)// check for property assignment
			{
				System.err.println ("TestContext: Error: unexpected argument - " + arg);
				usage(false);
				return false;
			}
			else // handle properties
			{
				int equalSign = arg.indexOf("=");
				String propName = arg.substring(0,equalSign);
				String propValue = arg.substring(equalSign+1);
				if (!TestParam.exists(propName))
					userParms.add(propName);
				setProperty(propName.toLowerCase(), propValue);  // coerce to lower case
			}
		}//end for

		if (showHelp)
		{
			usage(true);
			return false;
		}
		
		if (isInteractive && !isConsole) {  // impossible to prompt if running headless
			System.err.println("TestContext: : -prompt incompatible with -headless, ignoring.");
			isInteractive =  false;
		}

		if (isInteractive)
			promptForProps(userParms);
		
		// Append category and metadata for any user properties
		TestParam.addCategory("User properties not used by Bench Driver");
		Iterator<String> itr = userParms.iterator();
		while (itr.hasNext()) {
			TestParam.add(itr.next());
		}
		this.validate();
		if (isInteractive)
			exportProperties();   // give user a chance to save updated properties file
		
		return true;
	}

	/** Loop that prompts console user for each parameter, allowing optional modification or help text display.
	 * Note: should add a way to check if program is headless, i.e. running in background, and skip this logic.
	 */
	protected void promptForProps ( ArrayList<String> userParms )
	{
		System.err.println("For each property I will print the current value, then prompt you for options:");
		System.err.println("\tReply '?' for more a more detailed description of the property");
		System.err.println("\tReply 'X' to change the value of the property");
		System.err.println("\tReply 'D' to revert to the default value for the property");
		System.err.println("\tReply 'B' to go Back to the previous param");
		System.err.println("\tReply 'Q' to quit entering params and continue test harness");
		System.err.println("\tDefault is to keep this value for the property");
		System.err.println("Always terminate your response, even if null, with a carriage return");
		int indx = 1;  // skip mode when prompting - this is already set.
		TestParam[] paramlist = TestParam.allParams();  //Need array instead of Iterator to allow 'back' function
		while (indx < paramlist.length)
		{
			TestParam param = paramlist[indx];
			if (param.paramType == TestParam.CATEGORY_PARAM )  // category header
			{
				System.err.println(param);
				indx++;
			}
			else if (param.paramType == TestParam.USER_PARAM
					|| (param.paramType == TestParam.STANDARD_PARAM && param.isRelevant(this)))
			{
				String name = param.paramName;
				System.err.println("\n" + name + " = " + getProperty(name.toLowerCase()) + "\t\t(" + param.promptTxt + ")");
				System.err.println("\t Action(?|X|D|B|Q|<cr>):");
				try
				{
					String response = sysin.readLine().toLowerCase();
					if (response.startsWith("?"))
						System.err.println("\n\t" + param.promptTxt + ": " + param.helpTxt);
					else if (response.startsWith("d"))
						setProperty(name, param.defaultVal);
					else if (response.startsWith("x"))
					{
						System.err.print("\t"+name+"=");
						String val = sysin.readLine().trim();
						setProperty(name, val);
					}
					else if (response.toLowerCase().startsWith("b"))
					{
						indx--;
						while (indx > 0 && 
								(paramlist[indx].paramType == TestParam.CATEGORY_PARAM) // skip any category headers 
								|| !(paramlist[indx].isRelevant(this))) // skip
							indx--;
					}
					else if (response.toLowerCase().startsWith("q"))
						indx = paramlist.length;
					else
						indx++;
				}
				catch(IOException ioe)
				{
					ioe.printStackTrace();
					System.exit(-1);
				}
			}
			else
				indx++;
		}
		boolean enteringCustomParams = true;
		System.err.print("\n\nNow you can enter custom parameter/property values (enter 'q' to quit).\t");
		if (isVerbose)
			System.err.println("These parameters may be used in a results file (if resultsfile != 'none);; "
					+ "you can also include JNDI parameters by prefixing them with the string 'jndi.'.");
		while (enteringCustomParams)
		{
			try {
				System.err.print("\n\nEnter param name or 'q(uit)': ");
				String newparam = sysin.readLine().trim();
				if (newparam.equalsIgnoreCase("q") || newparam.equalsIgnoreCase("quit") || newparam.equals(""))
					enteringCustomParams = false;
				else
				{
					System.err.print("\t"+newparam+"=");
					String val = sysin.readLine();
					if (!TestParam.exists(val))
						userParms.add(newparam);
					super.setProperty(newparam, val);
				}
			} catch (IOException ioe) {
				ioe.printStackTrace();
				System.exit(-1);
			}
		}
	}
	
	/** Text describing command syntax.
	 * Prints command args, and optionally property options, to System.err.
	 * @param isLongVersion If true, print help for all properties as well.
	 */
	public void usage ( boolean isLongVersion )
	{
		System.err.println("usage: java bench.Driver [options ...] [prop=VAL] ...\n\n");
		System.err.println("options:\n");
		System.err.println("  -help             Print this help text.\n");
		System.err.println("  -verbose          Prints more detailed help, status and error messages.\n");
		System.err.println("  -props propfile[,propfile2 ...]   Load one or more test properties files.\n");
		System.err.println("  -prompt           Interactively override properties and cmd args.\n");
		System.err.println(" prop=VAL ...       Command line property assignments; standard property names will be converted to lower case.\n");
		if (isLongVersion)
			System.err.println("\n" + HELP_EXPLANATION + "\n");
		if (isLongVersion)
		{
			System.err.println("\nProperties:\n");
			for (Iterator<TestParam> itr = TestParam.list(true); itr.hasNext();)
			{
				Object item = itr.next();
				System.err.println(item.toString());
			}
		}
	}
	
	/**Check validity of configured test params.
	 * @return true if only warnings encountered, false if fatals encountered.
	 */
	public boolean validate ( )
	{
		boolean isValid = true;
		
		for (Iterator<TestParam> itr = TestParam.list(false);itr.hasNext();)
		{
			TestParam parm = (TestParam) itr.next();
			if (parm.paramType == TestParam.STANDARD_PARAM) {
				if (!parm.isValid(getProperty(parm.paramName)))
				{
					System.err.println ("TestContext: Parameter "+parm.paramName+"="+getProperty(parm.paramName)+": failed validation; replacing with default value "+parm.defaultVal);
					setProperty(parm.paramName, parm.defaultVal);
				}
			}
		}
		// Now check ranges for specific items
		if (getString("starter").equals("Prompt") && !TestContext.isConsole) { // can't wait for prompt in headless mode
			setProperty("starter", "Auto");
			System.err.println("TestContext: TestParam.Validate: Cannot use starter mode 'prompt' when running in -headless mode;  defaulting to 'auto' start");
		}
/* EXAMPLE VALIDATIONs
		if (getInt("destspersession") > getInt("numdests"))
		{
			isValid = false;
			System.err.println("TestContext: TestParam.Validate: Illegal to have destspersession > numdests");
		}
*/
		return isValid;
	}

	void exportProperties ( ) {
		try
		{
			System.err.print("\nSave settings to a property file (Y/N)?");
			String response = sysin.readLine().trim();
			if (response.equalsIgnoreCase("y"))
			{
				System.err.print("\n\tProperties file path (" + propFile + "):");
				String outfile = sysin.readLine().trim();
				if (outfile == null || outfile.length() < 1)
					outfile = propFile;
				writeConfig(new PrintStream(new FileOutputStream(outfile)));
			}
		}
		catch(IOException ioe) { ioe.printStackTrace(); System.exit(-1); }
	}

	/**print out full TestParams configuration */
	public void writeConfig ( PrintStream out )
	{
		for (Iterator<TestParam> itr = TestParam.list(true); itr.hasNext();)
		{
			TestParam param = itr.next();
			if (param.paramType == TestParam.CATEGORY_PARAM)  // category header
			{
				out.println(param);
			}
			else
			{
				String name = param.paramName;
				if (param.paramType == TestParam.STANDARD_PARAM
				  || param.paramType == TestParam.USER_PARAM) {
					if (isVerbose || param.isRelevant(this))
						out.println("\t" + name + "=" + getProperty(name.toLowerCase()) );
				}
			}
		}
	}

	/**print out current values of computed params */
	public void printEnv ( PrintStream out )
	{
		out.println("\nBuilt-in Test Variables:");
		for (Iterator<TestParam> itr = TestParam.list(true); itr.hasNext();)
		{
			TestParam param = itr.next();
				if (param.paramType == TestParam.COMPUTED_PARAM)
					out.println("\t" + param.promptTxt + " = " + param.value.compute());
		}
		out.println("\n");
	}

	/**
	 * Uniform method for extracting int values from 'number' type parameters.
	 * This utility method allows you to specify values in whole numbers, kilobytes, megabytes or gigabytes 
	 * (similar to the -Xms arg in the java cmd). If it cannot expand the string, it rethrows the NumberFormatException
	 * to the caller.
	 */
	public static int getIntValue ( String input ) {
		String numericPart = input;
		int scale = INT_SCALARS.indexOf(input.substring(input.length()-1));
		boolean isScaled = false;
		if (scale >= 0 && input.length() > 1) {
			isScaled = true;
			numericPart = input.substring(0,input.length()-1);  // trim off the scale character
		}
		int value = Integer.parseInt(numericPart);
		if (isScaled) {   // the scale is 'k', 'm' or 'g'
			value = value*1024;
			if (scale >0)  // the scale is 'm' or 'g'
				value = value*1024;
			if (scale > 1)  // the scale is 'g'
				value = value*1024;
		}
		return value;
	}

	/** Default value for maximum time in msecs JDBC client will wait for a reply. */
	public static final int REPLYTO_WAIT_TIMEOUT = 60000;
	/** Number of milliseconds to sleep after each Send, during the test warmup period.
	 * This prevents Producer threads from overwhelming the system as it warms up. If doWarmup=false,
	 * this won't be used. */
	public static final int MIN_STARTUP_SLEEP_MSECS = 20;

	// Formating date/times
	private static java.text.DateFormat DATE_FORMAT;
	static
	{
		DATE_FORMAT = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT);
		if (DATE_FORMAT instanceof java.text.SimpleDateFormat)
			((java.text.SimpleDateFormat)DATE_FORMAT).applyPattern("yy/MM/dd kk:mm:ss");
	}
	
	/** Static string with detailed help info */
	public static final String HELP_EXPLANATION
	= "If you specify a properties file, these override the default values (shown below); command line args (e.g. 'sleepTime=10') override properties, and interactive assignments (via the -prompt option) override everything."
		+ "\nWith the -prompt option, you can create a new properties file holding the final values; this can then be input via the -props arg.";

	static
	{
		TestParam.addCategory("DB Connection options");
		TestParam.add("host", "Database host", "localhost", "string", "String representing host name or ip address of JDBC Server.", "","");
		TestParam.add("port", "Database port", "3306", "number", "Socket port for the Database host connection.", "1to65000","");
		TestParam.add("dbname", "Database Name", "test", "string", "Name of default SQL database.", "","");
		TestParam.add("user", "User name for database connection", "root", "string", "String value of database login user name.", "","");
		TestParam.add("password", "Password for database connection", "password", "string", "String value of database login user password.", "","");
		
		TestParam.addCategory("Script options");
		TestParam.add("scriptclass", "Java class for Script implementation", "bench.GenScript", "string", "Implementation of bench.Script to be used to generate SQL commands.", "", "");
		TestParam.add("runscriptvars", "Script variables", "none", "string", "Either a) a pathname to a Properties file, or b) a list in format: '{var1=val1,var2=val2,...}'", "","");
		TestParam.add("runscriptfile", "SQL Generator Script File Path", "./sql_script.gen", "string", "Filesystem path for a bench.gen text generation script; output must be a list of valid SQL commands.", "", "");
		TestParam.add("loadscriptvars", "Script variables for load", "none", "string", "Either a) a pathname to a Properties file, or b) a list in format: '{var1=val1,var2=val2,...}'", "","");
		TestParam.add("loadscriptfile", "SQL Generator Script File Path for load", "./sql_script.gen", "string", "Filesystem path for a bench.gen text generation script; output must be a list of valid SQL commands.", "", "");

		TestParam.addCategory("Test timing and measurement");
		TestParam.add("sleepmsecs", "Client Sleep Time (msec)", "0", "number", "Number of milliseconds client will sleep between SQL calls; use this to control the rate at which producers will send.", "", "");
//		TestParam.add("sleepvarmsecs", "Random deviation factor for Client Sleep Time (msec)", "0", "number", "If non-zero, a random variation with this Standard Deviation will be added or subtracted to sleep time; only applies if sleepmsecs > 0.", "0to1m", "sleepmsecs!=0");
		TestParam.add("numrptintervals", "Number of report intervals", "10", "number", "Number of test intervals executed bedore exiting.", "", "");
		TestParam.add("rptintervalsecs", "Report Interval (secs)", "60", "number", "Interval between reporting output in seconds.", "", "");
		TestParam.add("nummessages", "Number of queries to process (zero if unlimited)", "0", "number", "After warmup, Client will process this number of queries, then stop; if zero, uses Long.MAX_VALUE for limit.", "", "");
//		TestParam.add("checklatency", "Measure and report send/receive Latency (true/false)", "false", "bool", "Latency is computed for updates and selects independently, DML statements are not included in latency stats.", "", "");
//		TestParam.add("checkreturncount", "Measure and report row count (true/false)", "false", "bool", "Row count body size is collected for SELECT statements only.", "", "");
//		TestParam.add("resultsfile", "Results Output File", "none", "string", "Output file for comma-separated results data (append mode), or 'none' to bypass results file.", "", "");
//		TestParam.add("variablelist", "List of Independent Variables", "sleepmsecs,clientid,$hostname,$datetime", "string", "List of param names to be used as independent variables in CSV output file; default setting includes the most popular output variables, set to 'none' to disable.", "", "resultsfile!=none");
		
		TestParam.addCategory("Driver specs");
		TestParam.add("driverid", "Unique test driver ID", "0", "number", "Integer identifying a specific Driver instance, used to generate unique clientID for thread/session/connection.", "0to1000", "");
		TestParam.add("starter", "Test start mechanism (Auto,Prompt,Socket)", "Prompt", "enum", "How to trigger the start of test iterations.  Options are:\n\t\tAuto: begin iterations immediately after initialization without waiting\n\t\tPrompt: prompt terminal for enter key (default)\n\t\tCallback: wait for parent container to start Clients", "Auto|Prompt|Socket", "");
		TestParam.add("doload", "Perform database load prior to test run (true/false)", "false", "bool", "If true, run a singleton Client using the loadscriptfile script and loadscriptvars script params; if false, skip load and go directly to test runs.", "", "");
		TestParam.add("numclients", "Number of Client threads to launch", "1", "number", "Driver will launch and monitor this many Client threads.", "1to1000", "");

		TestParam.addComputedValue("$datetime", "Current system date and time.", new ComputedValue() {
			public String compute ( ) {
				return (new Date().toString());
			}
		});
		TestParam.addComputedValue("$memory", "Current Java VM memory usage.", new ComputedValue() {
			public String compute ( ) {
				return new Long(Runtime.getRuntime().totalMemory()).toString();
			}
		});
		TestParam.addComputedValue("$hostname", "Local host name.", new ComputedValue() {
			public String compute ( ) {
				try {
					return InetAddress.getLocalHost().getHostName();
				} catch (UnknownHostException e) {
					return "Inaccessible";
				}
			}
		});
		TestParam.addComputedValue("$hostip", "Local host IP address.", new ComputedValue() {
			public String compute ( ) {
				try {
					return InetAddress.getLocalHost().getHostAddress();
				} catch (UnknownHostException e) {
					return "Inaccessible";
				}
			}
		});
		TestParam.addComputedValue("$os", "Operating system specification for local platform.", new ComputedValue() {
			public String compute ( ) {
				String osstring = System.getProperty("os.name")+"-"
								+ System.getProperty("os.arch")+"-"
								+ System.getProperty("os.version");
				return osstring;
			}
		});
		TestParam.addComputedValue("$user", "User name used to launch test harness process.", new ComputedValue() {
			public String compute ( ) {
				return System.getProperty("user.name");
			}
		});
		TestParam.addComputedValue("$javarev", "Java version for Driver client.", new ComputedValue() {
			public String compute ( ) {
				return (System.getProperty("java.vendor")+"/"+System.getProperty("java.version"));
			}
		});

	} // end static initialization of params
	
	/**
	 * Inner class to manage format and definitions for all parameters.
	 * All parameter names are coerced to lower case before being stored, to minimize the risk of
	 * mixed case spelling errors. Two static collections are managed within this class, one to support
	 * name-based lookup and the other to support output in printout sequence (including category names).
	 */
	public static final class TestParam
	{
		/** name of property */
		String paramName; 
		/** text displayed when prompting for property value */
		String promptTxt; 
		/** initial default value */
		String defaultVal;
		/** data type, can be string, bool, number (int), enum or 'computed' */
		int dataType;
		/** only for params of type TestParam.COMPUTED_PARAM, the object to compute the value. */
		ComputedValue value = null;
		/** text displayed for verbose help on the property */
		String helpTxt;
		/** Specification for validation of data values, based on type.  For type bool it is "true|false";
		 * for type int it is "n-m" where n and m are the min and max values; for type enum, it is in
		 * format "a|b|c...", a list of string values, one of which must match.
		 */
		String validationInfo;
		/** Relevance of parameter based on some other param. This consists of a single simple expression that
		 * will be parsed to yield a boolean value. If this expression is null or evaluates to true, it will
		 * be included when properties are prompted or printed, otherwise the value is nulled and the param
		 * is skipped in input and output. This prevents irrelevant parameters from cluttering up the display.
		 * <p>The specification must be in the format "PARM_NAME==STRING_VAL" or "PARM_NAME!=STRING_VAL"; the
		 * string value "NULL" equates to an empty string or null value, and the comparison is not case-sensitive.
		 * This comparison is performed by the isRelevant() method, which will only be invoked after all defaults,
		 * properties files and command line args have been processed.
		 */
		String relevanceInfo;
		/**
		 * List of canonical enum values, applies only to enum type parameters.
		 */
		private String[] enumValues = null;

		public static final int STANDARD_PARAM = 1;
		public static final int CATEGORY_PARAM = 2;
		public static final int USER_PARAM = 3;
		public static final int COMPUTED_PARAM = 4;

		public static final int UNDEFINED_DATATYPE = 0;
		public static final int STRING_DATATYPE = 1;
		public static final int BOOL_DATATYPE = 2;
		public static final int NUMBER_DATATYPE = 3;
		public static final int ENUM_DATATYPE = 4;
		public static final int COMPUTED_DATATYPE = 5;
		public static String[] DATA_TYPE = {"undefined","string","bool","number","enum","computed"};

		/** Type of TestParam, STANDARD_PARAM, USER_PARAM, CATEGORY_PARAM or COMPUTED_PARAM. Determines what metadata is kept,
		 * what collections it's entered in, and how values are extracted.*/
		int paramType;
		
		public TestParam ( String paramNameIn,
				String promptTxtIn,
				String defaultValIn,
				String dataTypeIn,
				String helpTxtIn,
				String validationInfoIn,
				String relevanceInfoIn )
		{
			paramName = paramNameIn.toLowerCase();
			promptTxt = promptTxtIn;
			defaultVal = defaultValIn;
			helpTxt = helpTxtIn;
			validationInfo = validationInfoIn;
			relevanceInfo = relevanceInfoIn;
			paramType = STANDARD_PARAM;
			
			if (dataTypeIn.equalsIgnoreCase("string"))
				dataType = STRING_DATATYPE;
			else if (dataTypeIn.equalsIgnoreCase("bool"))
				dataType = STRING_DATATYPE;
			else if (dataTypeIn.equalsIgnoreCase("number"))
				dataType = NUMBER_DATATYPE;
			else if (dataTypeIn.equalsIgnoreCase("enum"))
				dataType = ENUM_DATATYPE;
			else 
				dataType = UNDEFINED_DATATYPE;
			
			if (dataType == ENUM_DATATYPE) {
				if (validationInfo == null || validationInfo.length() == 0)
					throw new RuntimeException("TestParams: Enum parameter type declared without corresponding Validation Info to define enum values: "+paramName);
				enumValues = validationInfo.split("\\|");
				for (int i=0; i<enumValues.length; i++) {
					if (enumValues[i].length() == 0)
						throw new RuntimeException("TestParams: Enum value list includes empty string; format is 'VALUE [|VALUE]...'."
								+ "\n\t For parameter " + paramName + " validation info = " + validationInfoIn);
				}
			}
		}

		/**
		 * Special constructor for miscellaneous String param types, including USER_PARAM and CATEGORY_PARAM.
		 * @param paramNameIn For USER_PARAM type, the Property key, for CATEGORY_PARAM the display text.
		 * @param type Must be USER_PARAM or CATEGORY_PARAM
		 */
		public TestParam ( String paramNameIn, int type )
		{
			if (type != USER_PARAM && type != CATEGORY_PARAM)
				throw new RuntimeException("[TestParams] Illegal param type for this constructor: "+type);
			if (type == USER_PARAM) {
				paramName = paramNameIn.toLowerCase();
				promptTxt = paramNameIn;
				defaultVal = "undefined";
				helpTxt = "User defined property, used only for Test Harness report.";
			}
			else {
				paramName = paramNameIn;
			}
			dataType = STRING_DATATYPE;
			validationInfo = "";
			paramType = type;
		}

		/**
		 * Constructor for computed parameters.
		 * These params are computed based on a Java object that implements the ComputedValue interface.
		 * There should never be a case where a default value is required.
		 * @param paramNameIn Parameter name; by convention all computed value parameters use prefix '$'.
		 * @param helpTxtIn Help text description for interactive and documentation purposes.
		 * @param valueIn An instance of ComputedValue used to dynamically compute a string result.
		 */
		public TestParam ( String paramNameIn, String helpTxtIn, ComputedValue valueIn )
		{
			paramName = paramNameIn.toLowerCase();
			promptTxt = paramNameIn;
			defaultVal = "undefined";
			value = valueIn;
			helpTxt = "Computed property: "+helpTxtIn;
			validationInfo = "";
			paramType = COMPUTED_PARAM;
			dataType = COMPUTED_DATATYPE;
		}

		/**
		 * Validate that the parameter value is in to configured range for that Param type.
		 * Validation depends on the param type:
		 * <ol><li>number: with a min/max range, with * representing +or - infinity</li>
		 * </ol>
		 * @param valueIn
		 * @return
		 */
		public final boolean isValid ( String valueIn )
		{
			String value = valueIn;
			if (dataType == BOOL_DATATYPE)
			{
				if (value.equals("true") || value.equals("false"))
					return true;
				else
					return false;
			}
			else if (dataType == NUMBER_DATATYPE)
			{
				try
				{
					int numVal = getIntValue(value);
					if (validationInfo.length() > 0)
					{
						int delimOffset = validationInfo.indexOf("to");
						if (delimOffset >0)
						{
							int min = getIntValue(validationInfo.substring(0,delimOffset).trim());
							int max = getIntValue(validationInfo.substring(delimOffset+2).trim());
							if (numVal >= min && numVal <= max)
								return true;
							else
								return false;
						}
					}
				}
				catch (NumberFormatException e)
				{
					System.err.println("TestContext: Invalid number format validating parameter; param value="+value+", range="+validationInfo);
					return false;
				}
			}
			else if (dataType == ENUM_DATATYPE)
			{
				int enumLookupResult = lookupEnumIndex(value);
				if (enumLookupResult >= 0)
					return true;
				else if (enumLookupResult == -1)
					return false;
				else if (enumLookupResult == -2) {
					System.err.println(String.format("TestContext: WARNING - enum parameter %s value %s is not a case-sensitive match for one of: %s", paramName, value, validationInfo));
					return false;
				}
			}
			return true;
		}

		/** Lookup enum index.
		 * Must match one of the strings in the validation list for the enum parameter type.
		 * @return Matching index (0 - N) if value found in validation list; -1 if no match, -2 if near-match.
		 */
		public int lookupEnumIndex ( String value ) {
			boolean nearMatch = false;
			if (dataType != ENUM_DATATYPE)
				throw new RuntimeException("TestParams: Enum lookup attempted but param is not type 'enum'.");
			for (int i=0; i<enumValues.length; i++) {
				if (enumValues[i].equals(value))
					return i;
				else if (enumValues[i].equalsIgnoreCase(value))
					nearMatch = true;
			}
			if (nearMatch)
				return -2;
			else
				return -1;
		}

		/** Determine whether any dependencies have been met, and therefore the parameter is relevant
		 * for presentation to the user.
		 */
		public boolean isRelevant ( TestContext params ) {
			if (paramType == USER_PARAM)
				return true;
			boolean result = true;
			if (relevanceInfo != null && !relevanceInfo.equals("")) {
				String[] str = null;
				if (relevanceInfo.contains("==")) {
					str = relevanceInfo.split("==", 2);
					String val = params.getProperty(str[0].toLowerCase());
					if (!val.equalsIgnoreCase(str[1]))
						result = false;
				}
				else if (relevanceInfo.contains("!=")) {
					str = relevanceInfo.split("!=", 2);
					String val = params.getProperty(str[0].toLowerCase());
					if (val.equalsIgnoreCase(str[1]))
						result = false;
				}
				else
					System.err.println("TestContext: Error in Relevance spec: "+relevanceInfo);

			}
			return result;
		}
		
		/** Print the contents of this property as a formatted String. */
		public final String toString ( )
		{
			if (paramType == CATEGORY_PARAM)
				return paramName;
			StringBuffer buffer = new StringBuffer();
			buffer.append("\t").append(paramName).append(paramName.length() < 8 ? "\t" : "");
			buffer.append("\tType=").append(DATA_TYPE[dataType]);
			buffer.append("\tDefault=").append(defaultVal);
			buffer.append("\t").append(promptTxt);
			if (isVerbose)
				buffer.append("\n\t\t[").append(helpTxt).append("]");
			return buffer.toString();
		}
		
		/** Static hash table to look up Param Defs by name */
		static volatile HashMap<String,TestParam> PARAM_MAP = new HashMap<String,TestParam>();
		/** Static list of params in printout sequence. */
		static volatile Vector<TestParam> PARAM_LIST = new Vector<TestParam>();
		
		/** Static method to register param definitions.
		 * This method is used to insert all Driver param definitions into the static metadata collection.
		 */
		static void add ( String paramNameIn,
				String promptTxtIn,
				String defaultValIn,
				String dataTypeIn,
				String helpTxtIn,
				String validationInfoIn,
				String relevanceInfoIn )
		{
			TestParam param = new TestParam(paramNameIn,promptTxtIn,defaultValIn,dataTypeIn, helpTxtIn,validationInfoIn,relevanceInfoIn);
			PARAM_MAP.put(param.paramName, param);
			PARAM_LIST.add(param);
		}
		
		/** Runtime method to register user param definitions.
		 * Used to add user-defined params and others not included in the static table, above. 
		 * If these conflict with an existing driver param, this call is ignored.
		 */
		static void add ( String paramNameIn )
		{
			if (!PARAM_MAP.containsKey(paramNameIn))
			{
				TestParam param = new TestParam(paramNameIn, USER_PARAM);
				PARAM_MAP.put(param.paramName, param);
				PARAM_LIST.add(param);
			}
		}
		
		/** Static method to insert param category name into printout definition. */
		static void addCategory ( String categoryNameIn )
		{
			String catString = "#  **** " + categoryNameIn + " ****";
			TestParam param = new TestParam(catString, CATEGORY_PARAM);
			PARAM_LIST.add(param);
		}
		
		/** Static method to insert a computed, algorithmic parameter [internal use only].
		 * Common computed params are datetime, hostname, etc.  You can lookup values of 
		 * these parameters, but they are not included in the prompt/validate list. */
		static void addComputedValue ( String name, String helpTxtIn, ComputedValue value ) {
			TestParam param = new TestParam(name, helpTxtIn, value);
			PARAM_MAP.put(param.paramName, param);
			PARAM_LIST.add(param);
		}
		
		/** Runtime method to register vendor extension param definitions.
		 * Used to add params defined in a vendor extension class. 
		 * If these conflict with an existing driver param, the default param definition is overridden.
		 */
		static void addVendorParam ( TestParam param )
		{
			if (PARAM_MAP.containsKey(param.paramName)){
				Object old = PARAM_MAP.get(param.paramName);
				PARAM_MAP.remove(param.paramName);
				PARAM_LIST.remove(old);
			}
			PARAM_MAP.put(param.paramName, param);
			PARAM_LIST.add(param);
		}
		
		/** Test whether param definition is registered. */
		static boolean exists ( String key )
		{
			return PARAM_MAP.containsKey(key);
		}
		
		/** Return TestParam object matching key. */
		static TestParam get ( String key )
		{
			return (TestParam) PARAM_MAP.get(key.toLowerCase());
		}
		
		/** Return enumeration of TestParam definitions in printout sequence.
		 * Note this includes category headers.  You can use 'instanceof String' to identify these. 
		 * @param includeCategories If true, include category names and list in order, otherwise include only parameters.
		 */
		static Iterator<TestParam> list ( boolean includeCategories )
		{
			if (includeCategories)
				return PARAM_LIST.iterator();
			else
				return PARAM_MAP.values().iterator();
		}
		
		/** Return param list as an array. */
		static TestParam[] allParams ( )
		{
			return PARAM_LIST.toArray(new TestParam[PARAM_LIST.size()]);
		}
		
	} //end of inner class TestParam
	
	static interface ComputedValue
	{
		public String compute ( );
	} //end of inner Interface ComputedValue
	
	public static final class TestStats {
		public long errorCount;
		public long updateCount;
		public long queryCount;
		public long statementCount;
		
		public TestStats ( ) {
			errorCount = 0;
			updateCount = 0;
			queryCount = 0;
			statementCount = 0;
		}
		public TestStats ( Session session ) {
			this();
			errorCount = session.errorCount();
			updateCount = session.updateCount();
			queryCount = session.queryCount();
			statementCount = session.statementCount();					
		}

		public void tally ( TestStats stats ) {
			errorCount += stats.errorCount;
			updateCount += stats.updateCount;
			queryCount += stats.queryCount;
			statementCount += stats.statementCount;					
		}
		
		public String show ( ) {
			return String.format("\t# Statements\t%d\n\t# Queries\t%d\n\t# Updates\t%d\n\t# Errors\t%d", 
					statementCount, queryCount, updateCount, errorCount);
		}
	}
}