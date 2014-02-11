/* GenValue.java - Copyright (c) 2004 through 2008, Progress Software Corporation.
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

import java.text.DecimalFormat;
import java.util.Properties;

/**
 * Generates variable numeric values in String format.
 *   You configure the value generator with parameters of the processing instruction,
 * which are passed to the initialize() method.  Instructions are in the following format:
 * <p>  &lt?;gen.value order=XXX min=MM max=NN factor=FF format=DDD ratio=R.r save=VVV ?&gt;</p>
 * <pre>
 *  where XXX can be serial, random, context (default), or zipf
 *  MM is the minimum value to return (default 0)
 *  NN is the maximum value to return (default is DEFAULT_MAX_VALUE); (max - min) is used as a modulus for generated values
 *  FF is a factor by which the result will be multiplied (default 1)
 *  DDD is a valid java.text.DecimalFormat (default DFLT_FORMAT)
 *  R.r (if defined) is a floating point ratio by which the number will be multiplied, making the generated value floating point.</li>
 *  VVV is a new variable name where the current value will be stored for use by GenVariable.
 * </pre>
 *
 * Notes: you can use the range=0to10by2 option instead of min/max/factor. A ratio can be specified to generate decimal and/or
 * negative values, e.g. ratio=1.11 will create floating point results in a similar range to the computed integer values. When
 * using a ratio, it is generally wise to also specify a more suitable format.
 * 
 * The formulae generate using a Java 'long' type, limiting output to 9 or 10 digits; for longer strings (e.g. credit card numbers)
 * split the string into two smaller parts.
 *
 * @see bench.gen.GenSegmentBase
 * @author Progress Software Inc., all rights reserved.
 * @version TestHarness8.0
 */
public class GenValue
extends GenSegmentBase
{
	long          genCount_;  // Used for SERIAL and INCREMENTAL orders
	float         ratio_;     // Factor for decimal conversion
	boolean       isDecimal_; // Only if using Ratio - implies floating point result
	DecimalFormat format_;    // Decimal format for output value
	
	/**
	 * Simple constructor sets defaults - real setup is done by initialize().
	 */
	public GenValue ( Properties variables )
	{
		super(variables);
		genCount_ = 0;
		isDecimal_ = false;
		format_ = new DecimalFormat(DFLT_FORMAT);
	}
	
	/**
	 * Initialize the segment with input parameters.
	 *   The caller should pass in the attributes of the embedded processing instruction
	 * from the XML template.  Local attribute values for this subclass include:
	 *     format=XXX - where XXX is any valid DecimalFormat string
	 *
	 * @see java.text.DecimalFormat
	 * @param key An attribute name recognized by the segment type.
	 * @param val The value of that attribute.
	 * @return true if the name/value pair can be decoded, false otherwise.
	 */
	public boolean decode ( String key, String val )
	{
		if (key.equals("format"))
		{
			format_ = new DecimalFormat(val);
		}
		else if (key.equals("ratio"))
		{
			try
			{
				ratio_ = Float.parseFloat(val);
				isDecimal_ = true;
			}
			catch (NumberFormatException e) {ratio_ = 0;}
		}
		else
			return false;
		return true;
	}
	
	/**
	 * Generate a key value based on template specifications.
	 *
	 * @param context An integer distinguishing this iteration of the block.
	 * @return The formatted String representing the generated numeric value.
	 */
	public String generate ( long context )
	{
		long value = 0;
		if (dist_ == CONTEXT)
			value = transformLong(context);
		else if (dist_ == SERIAL)
			value = transformLong(genCount_);
		else if (dist_ == RANDOM)
			value = transformLong(randomLong());
		else if (dist_ == ZIPF)
			value = transformLong(zipfLong());
		else if (dist_ == LOG)
			value = transformLong(logDecayLong());
		genCount_++;
		
		String returnVal;
		if (isDecimal_)
			returnVal = format_.format(value * ratio_);
		else
			returnVal = format_.format(value);
		if (isSavingVar_)
			setVariable(saveVar_, returnVal);  // update value of global Variable.
		return returnVal;
	}
	
	/**
	 * Determine which kind of segment you have.
	 * @param segmentType An integer representing the enum value for segment type from GenSegment.
	 * @return true if the segment type is GenSegment.VALUE, false otherwise.
	 */
	public boolean isA ( int segmentType )
	{
		return VALUE == segmentType;
	}
	
	protected static final String DFLT_FORMAT = "#0";
	
} // end of class GenValue
