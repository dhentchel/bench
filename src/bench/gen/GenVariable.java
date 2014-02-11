/* GenVariable.java - Copyright (c) 2004 through 2008, Progress Software Corporation.
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

import java.util.Properties;
/**
 * Saves and returns back variable text.
 *   You configure the value generator with parameters of the processing instruction,
 * which are passed to the initialize() method.  Instructions are in the following format:
 * <pre>
 *   &lt;?gen.variable name=VVV default=DDD increment=II ?&gt;
 *   &lt;?gen.variable source=SSS ?&gt;
 * 
 *  VVV is the name of the variable
 *  DDD defines a default value to use if no 'save=' segment has been executed
 *  II is a numeric increment to be applied to the variable value:
 *       if VVV is numeric, it is incremented,
 *       otherwise a dummy variable is incremented and appended.
 *  SSS specifies that a group of variables are being defined; in this case, variables
 *    are created if they don't yet exist, and their default values are set. Valid
 *    formats for source include:
 *       source={var1=ValXX,var2=ValY}    (explicitly define variable/value list)
 *       source=filesystempath            (read name/value pairs from a properties file)
 *
 * A variable can be created and/or reset via a save=VVV attribute, e.g.:
 *   &lt;?gen.value modulus=NN format=DDD save=VVV?&gt;
 * </pre>
 * <p>GenVariables are stored in a Properties object shared by all child segments of the
 * root GenXml template; all generated text contains the last value set by the
 * static setVariable() method. This method is called in several ways:
 * <ol>
 * <li>if a Properties object is passed in to the GenXml constructor, it will define the default value; </li>
 * <li>if you call setVariables() on the root GenXml, it will override these initial values;</li>
 * <li>if any of the GenVariable segments with this key name use the default=XXX option, and no default has yet been defined, it becomes the default at parse time;</li>
 * <li>if any GenVariables use source=SSS to load variables, they override any previous defaults;</li>
 * <li>if GenXml.setVariables() is called after parsing, it again overrides any current values;</li>
 * <li>any segment (except GenBlock) that includes a save=VVV option will reset the variable VVV to the current value of that segment at generate time, thus overriding any of the above settings.</li>
 * </ol>
 * <pre>
 * DEVELOPER NOTE:  Need to better document the behavior of the increment option.
 * Future enhancements:
 *  1. Move static variables__ structure into thread-local heap, to make this thread safe.
 *  2. Possibly look in System.properties for any variables referenced but not defined.
 *  3. Allow a noprint option so you can define default values without inserting into the output.
 * </pre>
 *
 * @author Progress Software Inc., all rights reserved.
 * @version TestHarness8.0
 */

public class GenVariable
extends GenSegmentBase
{
	boolean incrementOn_;  // if increment option is specified
	int     increment_;  // value of increment option
	int     incrementBase_; // used if current variable value is non-numeric
	String  dfltValue_;
	String  source_;      // if specified, name cannot be
	
	/**
	 * Simple constructor sets defaults - real setup is done by initialize().
	 */
	public GenVariable ( Properties variables )
	{
		super(variables);
		dfltValue_ = "DFLT";
		source_ = "";
		incrementOn_ = false;
		incrementBase_ = 0;
	}
	
	/**
	 * Decode a parameter of the segment.
	 * @see bench.gen.GenSegmentBase#decode
	 * @param key An attribute name recognized by the segment type.
	 * @param val The value of that attribute.
	 * @return true if the name/value pair can be decoded, false otherwise.
	 */
	protected boolean decode ( String key, String val )
	{
		if (key.equals("default"))
		{
			dfltValue_ = val;
		}
		else if (key.equals("increment"))
		{
			incrementOn_ = true;
			increment_ = getIntParam(val, 1);
		}
		else if (key.equals("source"))
		{
			source_ = val;
		}
		else
			return false;
		return true;
	}
	
	/** Simple callback to verify params are reasonable, after block is constructed. */
	protected boolean validate ( )
	{
		if (name_ != null)
		{
			if (getVariable(name_).equals(""))  // if already set, leave as is
				setVariable(name_, dfltValue_);
			return true;
		}
		else if (source_ != null)
		{
			setVariables(source_);
			return false;
		}
		else
			return false;
	}
	
	/**
	 * Generate a key value based on template specifications.
	 *
	 * @param context An integer distinguishing this iteration of the block, currently ignored for variables.
	 * @return The formatted String representing the saved variable value.
	 */
	public String generate ( long context )
	{
		String val = getVariable(name_);
		if (incrementOn_)
		{
			try
			{
				int num = Integer.parseInt(val) + increment_;
				val = Integer.toString(num);
			}
			catch (NumberFormatException e)
			{
				incrementBase_ += increment_;
				val = new String(val + incrementBase_);
			}
		}
		return val;
	}
	
	/**
	 * Determine which kind of segment you have.
	 * @param segmentType An integer representing the enum value for segment type from GenSegment.
	 * @return true if the segment type is GenSegment.VARIABLE, false otherwise.
	 */
	public boolean isA ( int segmentType )
	{
		return VARIABLE == segmentType;
	}
	
} // end of class GenVariable
