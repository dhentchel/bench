/* GenSegmentBase.java - Copyright (c) 2004 through 2008, Progress Software Corporation.
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
package bench.gen;

import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.Random;
import java.util.StringTokenizer;

/**
 * Abstract implementation for GenSegment classes based on input processing instructions.
 * Generator Processing Instructions have the format:
 * <pre>
 *   &lt;?gen.TOKEN PARAM1=val PARAM2=val2... ?&gt;
 * 
 * General Parameters are described below:
 * Specific formats supported are:
 *   &lt;?gen.value order=XXX min=M factor=F format=DDD save=VVV ?&gt;
 *   &lt;?gen.date order=XXX increment=I type=TTT start=MM/DD/YYYY save=VVV ?&gt;
 *   &lt;?gen.words min=M max=N order=XXX source=SSS save=VVV ?&gt;
 *   &lt;?gen.begin min=M max=N ratio=0.RRR context=XXX ?&gt;
 *   &lt;?gen.end ?&gt;
 *   &lt;?gen.file source=FILEPATH ?&gt;
 *  where the various parameters (count, etc) are defined within the initialize method
 * (below) or the decode method of the specific implementation.
 * </pre>
 * <p>Note that most parameter values can be expressed as literals, or as pseudo-variables in the format $NAME.
 * Pseudo-variables are expanded as follows:<ol>
 * <li>The variable $RANDOM is expanded to a number less than DEFAULT_MAX_VALUE, expressed as a string.</li>
 * <li>The variable $ZIPF is expanded to a number less than DEFAULT_MAX_VALUE, expressed as a string.</li>
 * <li>Other variables must be set via Properties passed in at invoke time, or via the gen.variable block.</li>
 * </p>
 * <p>Note that the leading tag string "<?gen.value " and end tag " ?>" must match as a (case-insensitive)
 * literal, including period and terminating whitespace. The code should be cleaned up to
 * prevent truncation and case exceptions when values approach Integer.MAX_VALUE. In fact, all numeric
 * input params should be treated as longs, rather than ints.
 * </p>
 * @see bench.gen.GenBlock
 * @see bench.gen.GenSegment
 * @author Progress Software Inc., all rights reserved.
 * @version TestHarness8.0
 */
public abstract class GenSegmentBase
implements GenSegment
{
	Properties variables_;
	int id_;             // Unique number assigned to each segment; used as Random seed.
	String name_;        // Optional Segment name
	public String name() { return name_; };
	int    dist_;
	long   min_;
	long   max_;
	long   factor_;
	boolean isSavingVar_;  // If true, always update variable:  GenFile.setVariable(saveVar_, returnVal)
	String saveVar_;       // Name of variable to update
	
	private Random      rand_;
	private int         logBase_; // if order=logN this = N
	private long        randomSeed_ = 171931;  // should give strictly reproduceable results w multithreading
	private ZipfCreator zipfGenerator_;  // Zipf random number generator with zipfian distribution; used for word selection
	
	protected static volatile int SegmentCount = 0;
	
	protected GenSegmentBase ( Properties variables )
	{
		variables_ = variables;
		id_ = SegmentCount++;
		name_ = this.getClass().getName()+id_;
		dist_ = CONTEXT;
		min_ = 0;
		max_ = DEFAULT_MAX_VALUE;
		factor_ = 1;
		isSavingVar_ = false;
	}
	
	/**
	 * Retrieve GenVariable values for this template.
	 * The underlying Properties object is shared by all Segments of a tree, passed down from
	 * the root block via constructors. All keys are converted to lower-case. Default for undefined
	 * variables is to return an empty string.
	 */
	public String getVariable ( String key ) { return variables_.getProperty(key.toLowerCase(), ""); }
	/** Set the value of a GenVariable variable. */
	public void setVariable ( String key, String value ) { variables_.setProperty(key.toLowerCase(), value); }
	
	// Utility functions to generate or transform values
	/** Use Random class to generate unique ints, ranging from 1-modulus to modulus-1. */
	protected int randomInt ( int modulus ) { return random().nextInt(modulus); }
	/** Use Random class to generate unique, non-negative long integers. */
	protected long randomLong ( ) 
	{
		if (min_ == max_)
			return min_;
		else
			return (long) random().nextInt((int) max_); 
	}
	
	/** Compute integer with logarithmically diminishing occurrence. */
	protected long logDecayLong ( ) 
	{
		long result = 0;
		long base = 1;
		boolean test = true;
		long randomVal = random().nextLong();
		while (test)
		{
			base = base * logBase_;
			if ( randomVal % base == 0)
				result++;
			else
				test = false;
			randomVal++;  // increment val just to avoid any artifacts
		}
		return result;
	}
	
	
	/** Use ZipfCreator class to generate unique, zipf distribution integers. */
	protected int zipfInt ( ) { return zipfGenerator_.nextInt(); }
	/** Use ZipfCreator class to generate unique, zipf distribution integer, cast to a long. */
	protected long zipfLong ( ) { return (long) zipfGenerator_.nextInt(); }
	/** Set the upper limit for the zipf distribution. */
	protected int zipfLimit ( )
	{
		if (max_ - min_ > 0) {
			if (max_ - min_ > DEFAULT_ZIPF_MAX)
				return DEFAULT_ZIPF_MAX;
			else
				return (int) (max_ - min_);
		}
		else
			return DEFAULT_ZIPF;
	}
	
	/** Transform a long integer to another within the specified range for this segment */
	protected long transformLong ( long value )
	{
		if (min_ == max_)
			return min_;
		else
			return (value * factor_) % (max_ - min_ + 1) + min_;  
	}
	
	/**
	 * Initialize the segment with input parameters afnd/or literal text.
	 * Classes that extend this one must invoke super.initialize().
	 * If the type of segment == LITERAL, the params string consists of the literal
	 * XML text to be inserted.  If the segment is VALUE, WORDS or BLOCK the attributes
	 * of the embedded processing instruction are passed in.  For BLOCK segments, you
	 * must then call the parse() method so the block can scan for child segments.
	 * <p>
	 * In the next revision, most initialization will be performed directly in this class. For segment-specific
	 * params, a processParam(String) abstract method will be added to call back to the subclass. Generic params
	 * handled in this superclass will include:
	 * <ul>
	 * <li>name - mandatory only for GenVariable, can be used for identification of any segment</li>
	 * <li>order - the statistical distribution used to generate an integer value (context, serial, random, zipf, etc)</li>
	 * <li>min - minimum for generated integer value</li>
	 * <li>max - maximum for generated integer value (replaces count)</li>
	 * <li>factor - amount by which to multiply generated value (replaces increment)</li>
	 * <li>range - shorthand for the previous three: 'range=0to100by3'</li>
	 * <li>save - variable in which to save generated string</li>
	 * </ul>
	 * Other params that will still be decoded by the segment subclass:
	 * <ul>
	 * <li>context - (GenBlock) indicates an enum value defining how context values are generated within the block</li>
	 * <li>ratio - (GenBlock) decay ratio for repeating blocks (eventually move to 'exp' order type)</li>
	 * <li>while - (GenBlock) boolean expression telling whether to insert block</li>
	 * <li>start - (GenDate) Formatted date string to use as starting value</li>
	 * <li>time - (GenDate) replaced by additional 'type' enum values</li>
	 * <li>source - (GenWords, GenFile) file containing text strings or filesystem location</li>
	 * <li>format - (GenValue) Java DecimalFormat string used to format the number</li>
	 * <li>default - (GenVariable) default value of variable</li>
	 * </ul>
	 *
	 * @param params A list of name=value pairs, with whitespace delimiters, defining the attributes for the segment.
	 * @return true if initialization succeeded and segment should be included, false if this segment should be ignored.
	 */
	public boolean initialize ( String params )
	{
		StringTokenizer tokens = new StringTokenizer(params, " ?\t\n\r\f"); // delimit tokens based on whitespace or terminating '?' of processing instruction
		while (tokens.hasMoreTokens())
		{
			String param = tokens.nextToken().trim();
			String key = param.substring(0, param.indexOf("=")).toLowerCase();
			String val = param.substring(param.indexOf("=") + 1);
			if (key == null || val == null || key.length() < 2 || val.length() < 1)
				throw new RuntimeException("Parsing params at " + param + " - must specify both key and value strings");
			if (key.equals("name"))
			{
				name_ = val;
			}
			else if (key.equals("order"))
			{
				if (val.equalsIgnoreCase("serial"))
					dist_ = SERIAL;
				else if (val.equalsIgnoreCase("random"))
					dist_ = RANDOM;
				else if (val.equalsIgnoreCase("zipf"))
					dist_ = ZIPF;
				else if (val.equalsIgnoreCase("context"))
					dist_ = CONTEXT;
				else if (val.toLowerCase().startsWith("log"))
				{
					dist_ = LOG;
					try { logBase_ = Integer.parseInt(val.substring(3)); }
					catch (NumberFormatException e) { logBase_ = 10; }
				}
			}
			else if (key.equals("count"))
			{
				max_ = getIntParam(val, 1);
				min_ = max_;
			}
			else if (key.equals("min"))
			{
				min_ = getIntParam(val, 0);
			}
			else if (key.equals("max"))
			{
				max_ = getIntParam(val, DEFAULT_MAX_VALUE);
			}
			else if (key.equals("factor"))
			{
				factor_ = getIntParam(val, 0);
			}
			else if (key.equals("range"))
			{
				val = val.toLowerCase();
				if (val.indexOf("to") < 1) { System.err.println("GenSegmentBase: range option requires keyword 'to' in value string"); return false; }
				min_ = getIntParam(val.substring(0, val.indexOf("to")), 0);  // will fail if any non-numeric precedes 'to'
				if (val.indexOf("by") > val.indexOf("to"))
				{
					max_ = getIntParam(val.substring(val.indexOf("to")+2, val.indexOf("by")), 1);
					factor_ = getIntParam(val.substring(val.indexOf("by")+2), 0);
				}
				else
					max_ = getIntParam(val.substring(val.indexOf("to")+2), 0);
			}
			else if (key.equals("save"))
			{
				isSavingVar_ = true;
				saveVar_ = val;
			}
			else
			{
				if (!this.decode(key, val)) { System.err.println("GenSegment: unrecognized parameter: " + param); return false; }
			}
		}
		// Now do some validation that values are reasonable...
		if (max_ < min_)
			max_ = min_ + 1; // prevents invalid min/max range
		if (factor_ < 1)
			factor_ = 1;
		
		if (!this.validate())  // call back to subclass to double-check params are valid
			return false;
		
		if (dist_ == ZIPF)
			zipfGenerator_ = new ZipfCreator(zipfLimit());  // computation of max zipf value may be overridden by subclass
		return true;
	}
	
	/**
	 * Decode a parameter of the segment.
	 * <p>This is overriddent by the subclass to decode any non-standard parameters.
	 * All parameters must be in the format KEY=VAL, with whitespace between the
	 * parameter assignments, but no internal whitespace. The default implementation
	 * returns false, which triggers a "param not recognized" message from the
	 * initialize method.</p>
	 *
	 * @param key An attribute name recognized by the segment type.
	 * @param val The value of that attribute.
	 * @return true if the name/value pair can be decoded, false otherwise.
	 */
	protected boolean decode ( String key, String val ) { return false; }
	
	/** Simple callback to verify params are reasonable, after block is constructed. */
	protected boolean validate ( ) { return true; }
	
	/**
	 * Create the output XML text for this segment as a String.
	 * <p>If this is a block segment, it will create a ByteArrayOutputStream to
	 * collect the recursive output and use the alternate signature of generate
	 * to populate this stream, which is then converted to a String.</p>
	 * <p>The context number is passed in to allow unique, sequential generation,
	 * but whether it is used and how it is used depends on the 'order' parameter
	 * and the specific subclass implementation.</p>
	 *
	 * @param context A unique, sequentially ascending number generated by the caller.
	 * @return A String containing the generated output for this segment and any child segments.
	 */
	abstract public String generate ( long context );
	
	/**
	 * Generate the output XML text for this segment into an output stream.
	 * <p>This default implementation (not used by GenBlock) simply converts the output
	 * of the String generate method to a byte array in order to write it to the
	 * output stream. GenBlock overrides this to recursively generate text from all
	 * child segments. It also generates an input context value that is unique within
	 * file/block scope according to input specifications.</p>
	 * <p>This is the preferred api where performance is important, as it avoids
	 * repeated conversion of unicode characters to bytes in most situations, and
	 * scales well for stream-oriented output.</p>
	 * @param context A unique, sequentially ascending number generated by the caller.
	 * @param out The output stream into which generated text will be written.
	 */
	public long generate ( long context, OutputStream out )
	{
		byte[] bytes = generate(context).getBytes();
		try { out.write(bytes); }
		catch (java.io.IOException e) {
			throw new RuntimeException("generate: failure writing to output stream.", e);
			}
		return bytes.length;
	}
	
	/**
	 * Determine which kind of segment you have.
	 * Current valid segment types include: LITERAL, VALUE, WORDS, BLOCK, DATE and VARIABLE
	 * @param segmentType An integer representing the enum value for segment type from GenSegment.
	 * @return true if the segment type is the correct Segment type, false otherwise.
	 */
	abstract public boolean isA ( int segmentType );
	
	/**
	 * Convert an input param value to an integer.
	 * This routine validates that the input value is numeric, alternatively expanding any $var 
	 * expressions. The variables $random and $zipf are expanded into integers based on the 
	 * appropriate distribution. Other $XXX expressions are expanded by looking up the variable
	 * in the current context. If a variable does not exist or a string cannot be parsed into an int, the defaultVal is returned.
	 * @param val An input string from the genxml template representing an integer value.
	 * @param defaultVal The value to return if the val string can't be properly converted.
	 */
	protected int getIntParam ( String val, int defaultVal )
	{
		String num = val;
		if (val.substring(0,1).equals("$"))
		{
			if (val.substring(1).equalsIgnoreCase("RANDOM"))
				return randomInt(DEFAULT_MAX_VALUE);
			else if (val.substring(1).equalsIgnoreCase("ZIPF"))
				return zipfInt();
			else {
				num = getVariable(val.substring(1));
				if (num.equals(""))
					return defaultVal;
			}
		}
		try { return Math.abs(Integer.parseInt(num)); }
		catch (NumberFormatException e) { return defaultVal; }
	}
	
	/** Return a Random number generator.
	 * In order to introduce some variability into random number generation,
	 * each Random object is started with a large seed value that is incremented
	 * by the hashcode of the segment's name. Thus, you can manipulate the sequence
	 * for order=random by simply defining name=XXX in the instruction.
	 */
	protected Random random ( )
	{
		if (rand_ == null)
		{
			randomSeed_ += this.name_.hashCode();
			rand_ = new Random(randomSeed_);
		}
		return rand_;
	}
	
	/**
	 * Reset GenVariable values.
	 * <p>This may be called any time after construction, but is normally called just before the
	 * generate method to pass expanded Option values into the template. They may then be overridden
	 * again, if any of the child segments uses the save=VAR_NAME option to reset their value.</p>
	 * <p>The input string is not parsed with a tokenizer, so creative whitespace should be avoided.
	 * Variables may be explicitly passed using format: <i>{name1=val1,name2=val2,name3=val3}</i>.
	 * You should be especially careful to avoid any whitespace before or after each name, as the
	 * parser is very primitive. Alternatively, if the input string does not start with "{", it is
	 * assumed that the input arg is a local filepath of a Properties file to be ingested. Most
	 * errors result in a printed message, but no exception.</p>
	 *
	 * @param source A String indicating the names and values of variables to set; options are:
	 *        'none' - no variables to process, ignore
	 *        filepath - open properties file at this location
	 *        {var1=value,var2=value...} - literal list of key/value pairs.
	 */
	public void setVariables ( String source )
	{
		if (source.equalsIgnoreCase("none"))
			return;
		else if (source.startsWith("{"))
		{
			if (!source.endsWith("}")) { System.err.println("GenVariable: Illegal source syntax - " + source); }
			String parmString = source.substring(1, source.length() - 1);  // trim off the brackets
			int current = 0;
			int end = parmString.length();
			while (current < end)
			{
				int comma = parmString.indexOf(",", current);
				if (comma == -1)    // the last one
				{
					checkVariables(parmString.substring(current,end));
					current = end; //we're done
				}
				else
				{
					checkVariables(parmString.substring(current,comma));
					current = comma + 1;
				}
			}
		}
		else  // source is a file
		{
			try
			{
				FileInputStream varFile = new FileInputStream(source);
				variables_.load(varFile);
			}
			catch (Exception e) 
			{
				System.err.println("GenSegmentBase.setVariables: cannot parse variables file " + source); 
			}
		}
	}

	/**
	 * Update variables from a Properties object.
	 * @param source The properties file to be merged into default variables.
	 */
	public void setVariables ( Properties source )
	{
		variables_.putAll(source);
	}
	
	private void checkVariables (String nameValuePair )
	{
		int equals = nameValuePair.indexOf("=");
		if (equals == -1) { System.err.println("GenSegmentBase: Illegal variable value: " + nameValuePair); return ;}
		this.setVariable(nameValuePair.substring(0, equals), nameValuePair.substring(equals+1));
	}
	
	// ==================== static fields and methods ===========================
	
	protected static final int SERIAL = 1;  // Values increment from 0.
	protected static final int RANDOM = 2;  // Each value generated randomly
	protected static final int ZIPF = 3;    // Each value generated via Zipf distr
	protected static final int CONTEXT = 4; // Use context arg of generate()
	protected static final int LOG = 5;     // Use context arg of generate()
	
	protected static final long SEED_INCREMENT = 21001;
	protected static final int DEFAULT_MAX_VALUE = 1000000000;
	protected static final int DEFAULT_ZIPF = 99;
	protected static final int DEFAULT_ZIPF_MAX = 9999999;
	
	/**
	 * Internal class to compute a boolean condition.
	 * This class supports the 'while' parameter for various segment types. It allows boolean
	 * operators "=", "<" and ">". The operands can be $variable expressions, or integer constants.
	 * <p> The constructor parses the input condition string and sets up the comparison. Each invocation
	 * of test() requires that you pass in a GenSegmentBase to provide access to the getIntParam() method</p>
	 */
	protected static class Condition
	{
		private boolean isConst_ = false;
		private boolean constVal_ = true; // only used when isConst_==true
		private int operator_; //must be EQL, LT or GT
		private String leftOperand_;
		private String rightOperand_;
		
		/**
		 * If the original condition string cannot be determined, because of integer conversion errors, etc,
		 * this routine will print an error message, then return true. Variables are reevaluated every time
		 * @param expr An input string from the genxml template representing an integer value.
		 */
		protected Condition ( String expr )
		{
			int eql = expr.indexOf("=");
			int lt = expr.indexOf("<");
			int gt = expr.indexOf(">");
			int offset;
			if ( (eql * lt * gt <= 0) // zero or two matching operators
					|| (eql>0 && lt>0 &&gt>0)) // three matching operators
			{
				System.err.println("GenSegmentBase.Condition: invalid condition string: " + expr);
				isConst_ = true;
				return;
			}
			else  // compute operation type and offset within the expr string
			{
				if (eql>0) { operator_ = EQL; offset = eql; }
				else if (lt>0) { operator_ = LT; offset = lt; }
				else { operator_ = GT; offset = gt; }
			}
			leftOperand_ = expr.substring(0, offset);
			rightOperand_ = expr.substring(offset+1);
			if (!leftOperand_.substring(0,1).equals("$") && !rightOperand_.substring(0,1).equals("$"))
			{
				constVal_ = test(new GenVariable(null));  // no Properties needed, because expression has no variables
				isConst_ = true;
			}
		}
		
		/**
		 * Determine the current boolean value of the condition.
		 * Variable operands are recomputed, based on the Variables table of the segment passed in,
		 * and using defaults that will ensure a return of 'true' if they are invalid. The appropriate
		 * operator is applied, and the comparative value returned.
		 * @param seg A segment containing the variables used in expansion of operands.
		 * @return False if the condition can be determined and is not true, true otherwise.
		 */
		public boolean test ( GenSegmentBase seg )
		{
			if (isConst_)
				return constVal_;
			if (operator_ == EQL)
				return (seg.getIntParam(leftOperand_, 1) == seg.getIntParam(rightOperand_, 1));
			else if (operator_ ==  LT)
				return (seg.getIntParam(leftOperand_, 0) < seg.getIntParam(rightOperand_, 1));
			else
				return (seg.getIntParam(leftOperand_, 1) > seg.getIntParam(rightOperand_, 0));
		}
		
		protected static int EQL = 1;
		protected static int LT = 2;
		protected static int GT = 3;
		
	} // end of nested class Condition
} // end of class GenSegmentBase
