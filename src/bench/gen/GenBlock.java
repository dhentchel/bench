/* GenBlock.java - Copyright (c) 2004 through 2008, Progress Software Corporation.
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

import java.util.Vector;
import java.util.Enumeration;
import java.util.Properties;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;

/**
 * Generates blocks of xml text, customized based on syntax embedded in the template.
 *   In general, the generator program creates a single top-level instance of GenBlock
 * to manage the entire XML file. Other GenBlocks can be nested below it by specifying
 * the following syntax within the XML template:
 * <pre>
 *   &lt?gen.begin name=NAME count=N context=CONTEXT ?&gt</p>
 *     &lt-- misc XML text --&gt</p>
 *   &lt?gen.end name=NAME ?&gt</p>
 *       OR
 *   &lt?gen.begin name=NAME min=M max=N ratio=0.R context=CONTEXT ?&gt</p>
 *     &lt-- misc XML text --&gt</p>
 *   &lt?gen.end name=NAME ?&gt</p>
 *       OR
 *   &lt?gen.begin name=NAME range=MtoNby2 context=CONTEXT ?&gt</p>
 *     &lt-- misc XML text --&gt</p>
 *   &lt?gen.end name=NAME ?&gt</p>
 *  where
 * </pre>
 * <ol>
 * <li>NAME is an optional block name</li>
 * <li>N is the actual or maximum number of instances of the block to generate (default 1)</li>
 * <li>M (if defined) is the minimum number of instances</li>
 * <li>R (if defined) uses the floating point ratio (between 0 and 1) as logarithmic decay ratio.</li>
 * <li>CONTEXT can be incremental, nested or combined.</li>
 * </ol>
 * <p>If you define a minimum, then max is synonymous with count, and the number of generated
 * instances of the block will vary randomly between min and max.</p>
 * <p>Incremental implies every instance of the block will increase monotonically across all
 * instances of the block; Nested means that child blocks are given context numbers 0 to N-1,
 * restarting at zero for each new parent; Combined means that the nested count and parent's
 * context are combined to give a unique number; this is the same as incremental, unless the
 * calling program explicitly changes the top-level context or the min= attribute is used.
 * </p>
 * <pre>
 * Future enhancements:
 *  1. Replace ratio attribute with order=logN 
 * </pre>
 * @author Progress Software Inc., all rights reserved.
 * @version TestHarness8.0
 */
public class GenBlock
extends GenSegmentBase
{
	Vector<GenSegment> segmentList_; // list of GenSegments belonging to this block.
	long counter_;       // Count of record output number
	float ratio_;        // Factor for logarithmic distribution of block count
	boolean randomCount_;// Number of children vary randomly between min_ and max_
	boolean usingRatio_; // Factor number of blocks by ratio_ at each iteration
	boolean doTest_;
	GenSegmentBase.Condition testCondition_; // Optional comparison test to determine whether to print block.
	long parentContext_; // Parent context number, passed in to generate()
	int contextRule_;    // Rule for computing current context, must be INCREMENTAL, NESTED or COMBINED
	
	/** Context = counter_, across lifetime of block. */
	public static final int INCREMENTAL = 1;
	/** Context = counter_, zeroed out with each generate(). */
	public static final int NESTED = 2;
	/** Context combines parent context and counter. */
	public static final int COMBINED = 3;
	/** Starting size for xml string buffer. */
	public static final int BUFFER_SIZE = 640000;
	
	/**
	 * Simple constructor sets defaults.
	 *   The serious setup work is done by the initialize() and parse() methods.
	 */
	public GenBlock ( Properties variables )
	{
		super(variables);
		counter_ = 0;
		randomCount_ = false;  // By default, specify exact count of inserted blocks
		usingRatio_ = false;  // By default, uniform variation between min and max
		doTest_ = false;    // unless you specify a 'while' param, we'll skip this step
		contextRule_ = COMBINED;
		segmentList_ = new Vector<GenSegment>();
	}
	
	/**
	 * Initialize the segment with input parameters.
	 * <p>The caller should pass in the attributes of the embedded processing instruction
	 * from the XML template.  Valid local parameters include:
	 * <pre>
	 *   context=incremental - ignore input context, generate ascending integers
	 *   context=nested - generate ascending integers, but return to zero if input context changes
	 *   context=combined - multiply input context by count and add counter.
	 *   while=expression - expand block only if test(expression) returns true.
	 * </pre>
	 * After the GenBlock is initialized, you still have to call the parse() method so the
	 * block can scan for child segments.</p>
	 * <p>For min, max and count, you can specify either a valid integer string or '$VAR', where VAR
	 * is a gen.variable segment that has a defined or default value. This means you must somehow
	 * define the value of the variable prior to segment intialization, either by:
	 * <pre>
	 *   1. Using a gen.variable with default=val prior to this point in the template file
	 *   2. Supplying a properties object in the GenFile constructor.
	 *   3. Calling GenFile.setVariables("VAR=val"), <i>after</i> construction, but <i>before</i> parsing.
	 * </pre>
	 * </p>
	 *
	 * @param key An attribute name recognized by the segment type.
	 * @param val The value of that attribute.
	 * @return true if the name/value pair can be decoded, false otherwise.
	 */
	protected boolean decode ( String key, String val )
	{
		if (key.equals("context"))
		{
			if ("incremental".equalsIgnoreCase(val))
				contextRule_ = INCREMENTAL;
			else if ("nested".equalsIgnoreCase(val))
				contextRule_ = NESTED;
			else if ("combined".equalsIgnoreCase(val))
				contextRule_ = COMBINED;
		}
		else if (key.equals("ratio"))
		{
			try
			{
				ratio_ = Math.abs(Float.parseFloat(val));
				if (ratio_ >= 1.0)
					usingRatio_ = false;  // decay ratios must be < 1 to be meaningful
				else
					usingRatio_ = true;
			}
			catch (NumberFormatException e) {ratio_ = 0;}
		}
		else if (key.equals("while"))
		{
			testCondition_ = new GenSegmentBase.Condition(val);
			doTest_ = true;   // actual test will be performed during each execute iteration
		}
		else
			return false;
		return true;
	}
	
	/** Simple callback to verify params are reasonable, after block is constructed. */
	protected boolean validate ( )
	{
		if (usingRatio_)
		{
			if (max_ == min_)
			{
				System.err.println("GenBlock: ratio invalid when min == max");
				usingRatio_ = false;
			}
		}
		else // if no decay ratio specified, use flat distribution
		{
			if (max_ != min_)
				randomCount_ = true;
		}
		return true;
	}
	
	/**
	 * Process body of block and set up all child segments.
	 * <p>The input offset should point to the index within the char array following the
	 * BLOCK_START_TOKEN substring.  When the BLOCK_END_TAG is encountered or the end of
	 * the buffer is reached, the method exits.  Note that any whitespace following a
	 * block begin or end is discarded, making it easier to format the block parameters
	 * within the template.
	 * </p>
	 *
	 * @param template - input XML template.
	 * @param offset - index into template to begin parsing.
	 * @return index within template when end of this block is reached.
	 */
	public int parse ( String template, int offset )
	{
		int textStart = offset;
		int textEnd = 0;
		int paramStart = 0;
		int paramEnd = 0;
		while (textStart < template.length()) // Read next processing instruction plus preceding text
		{
			paramStart = template.indexOf(BEGIN_TAG, textStart);
			if (paramStart >= textStart)
			{
				textEnd = paramStart;
				paramEnd = template.indexOf(END_TAG, paramStart);
				if (paramEnd > paramStart)
				{
					paramEnd = paramEnd + END_TAG.length();
				}
				else
				{
					throw new RuntimeException("GenBlock: End tag missing on begin segment");
				}
			}
			else
			{
				paramStart = paramEnd = textEnd = template.length();
			}
			if (textStart != textEnd) // free text found
			{
				GenText segment = new GenText();
				segment.initialize(template.substring(textStart, textEnd));
				segmentList_.addElement(segment);
			}
			if (paramStart != paramEnd)  // Segment found
			{
				int beginToken = template.indexOf(BEGIN_TOKEN, paramStart + BEGIN_TAG.length()) + BEGIN_TOKEN.length();
				int endToken = template.indexOf(END_TOKEN, beginToken);
				String token = template.substring(beginToken, endToken);
				String arglist = template.substring(endToken + END_TOKEN.length(), paramEnd - END_TAG.length()).trim();
				GenSegment segment = null;
				try
				{
					if (token.equals(VALUE_TOKEN))
					{
						segment = new GenValue(variables_);
						if (segment.initialize(arglist))
							segmentList_.addElement(segment);
					}
					else if (token.equals(WORDS_TOKEN))
					{
						segment = new GenWords(variables_);
						if (segment.initialize(arglist))
							segmentList_.addElement(segment);
					}
					else if (token.equals(DATE_TOKEN))
					{
						segment = new GenDate(variables_);
						if (segment.initialize(arglist))
							segmentList_.addElement(segment);
					}
					else if (token.equals(VAR_TOKEN))
					{
						segment = new GenVariable(variables_);
						if (segment.initialize(arglist))
							segmentList_.addElement(segment);
					}
					else if (token.equals(GENFILE_TOKEN))
					{
						GenFile genfile = new GenFile( System.err, variables_);
						if (genfile.initialize(arglist))
						{
							segmentList_.addElement(genfile);
						}
					}
					else if (token.equals(BLOCK_START_TOKEN))
					{
						GenBlock block = new GenBlock( variables_);
						if (block.initialize(arglist))
						{
							segmentList_.addElement(block);
							while (Character.isWhitespace(template.charAt(paramEnd)) && !Character.isSpaceChar(template.charAt(paramEnd)))
								paramEnd++;  // skip linefeeds, tabs, etc. after block begin & end
							paramEnd = block.parse(template, paramEnd);
						}
					}
					else if (token.equals(BLOCK_END_TOKEN))
					{
						if (arglist.toLowerCase().startsWith("name="))
						{
							String name = arglist.substring(arglist.indexOf("=") + 1); // Assume only one param
							if (!name.equals(name_)) // if end block has a name, it must match begin block
								throw new RuntimeException("GenBlock: mismatched block begin/end names(" + name_ + "," + name + ")");
						}
						while (paramEnd < template.length() && Character.isWhitespace(template.charAt(paramEnd)) && !Character.isSpaceChar(template.charAt(paramEnd)))
							paramEnd++;  // skip linefeeds, tabs, etc. after block begin & end
						return paramEnd;
					}
					else if (token.equals(COMMENT_TOKEN)) // ignore comments
					{
						while (paramEnd < template.length() && Character.isWhitespace(template.charAt(paramEnd)) && !Character.isSpaceChar(template.charAt(paramEnd)))
							paramEnd++;  // skip linefeeds, tabs, etc. after comment
					}
					else
						throw new RuntimeException("GenBlock - Invalid template token");
				}
				catch (Exception e)
				{
					System.err.println("\n\n\nException parsing block!");
					e.printStackTrace();
					System.err.println("\n  Error may have occurred parsing argument list: " + arglist);
					int printEnd = 120 + textStart; // print a long substring
					if (printEnd > template.length()) printEnd = template.length();
					System.err.println("\nError occurred at text (offset " + textStart + "): " + template.substring(textStart, printEnd));
					throw(new RuntimeException(e.getMessage()));
				}
			}
			else
			{
				return textEnd;
			}
			textStart = paramEnd;  // Move pointer forward, look for next segment
		}
		// Possibly add a check here that this is the root block - otherwise we should still be going
		return textStart;
	}

	/**
	 * Create the output XML text for this block as a String.
	 * This will recursively generate text from all child segments, including any
	 * nested blocks.  The GenRecord object will use the input context to generate
	 * output context values according to the initialization() input args.
	 *
	 * @param context An integer distinguishing this iteration of the block.
	 * @return The XML text for this block, including any nested child blocks.
	 */
	public String generate ( long context )
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream(BUFFER_SIZE);
		generate(context, out);
		return out.toString();
	}
	
	/**
	 * Create the output XML text for this block and send to an output stream.
	 * This will recursively generate text from all child segments, including any
	 * nested blocks.  The GenRecord object will use the input context to generate
	 * output context values according to the initialization() input args.
	 *
	 * @param context An integer distinguishing this iteration of the block.
	 * @param out The OutputStream to write this block of XML output to.
	 */
	public long generate ( long context, OutputStream out )
	{
		if (doTest_ && !testCondition_.test(this))
			return 0;  // do NOT print this block this time
		long size = 0;
		parentContext_ = context;
		if (contextRule_ != INCREMENTAL) // contextRule_ == NESTED or COMBINED
			counter_ = 0;
		long numBlocks = max_; // by default use constant number of child blocks
		if (usingRatio_)
		{
			numBlocks = min_;
			for (long i = min_; i < max_; i++)
			{
				float val = random().nextFloat();
				if (val < ratio_)
					numBlocks++;
				else
					i = max_; // terminate loop  // should just use 'break'
			}
		}
		else if (randomCount_)
		{
			numBlocks = min_ + randomInt((int) max_ + 1 - (int) min_);
		}
		for (int i = 0; i < numBlocks; i++)
		{
			Enumeration<GenSegment> itr = segmentList_.elements();
			while (itr.hasMoreElements())
			{
				GenSegment segment = (GenSegment) itr.nextElement();
				size += segment.generate(context(), out);
			}
			counter_++; // counters will start at 0
		}
		return size;
	}
	
	/**
	 * Compute current context, based on context rules and counter.
	 * @return The context number used to generate data values.
	 */
	protected long context ( )
	{
		if (contextRule_ == INCREMENTAL || contextRule_ == NESTED)
			return counter_;
		else  // contextRule == COMBINED
			return parentContext_ * max_ + counter_;
	}
	
	/**
	 * Determine which kind of segment you have.
	 * @param segmentType An integer representing the enum value for segment type from GenSegment.
	 * @return true if the segment type is GenSegment.BLOCK, false otherwise.
	 */
	public boolean isA ( int segmentType )
	{
		return BLOCK == segmentType;
	}
	
} // end of class GenBlock
