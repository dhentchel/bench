/* GenText.java - Copyright (c) 2004 through 2008, Progress Software Corporation.
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

import java.io.OutputStream;

/**
 * Holds xml template text in String format.
 *   This implementation of GenSegment holds text segments, i.e. any free text that is not part
 * of a GenXml processing instruction.
 * 
 * @author Progress Software Inc., all rights reserved.
 * @version TestHarness8.0
 */

public class GenText
implements GenSegment
{
	byte[] text_;          // Literal text for this segment
	
	/**
	 * Simple constructor sets defaults - real setup is done by initialize().
	 */
	public GenText ( )
	{
		text_ = "<FillerText>Sample generated text inserted to take up space.</FillerText>".getBytes();
	}
	
	/**
	 * Initialize the segment with literal text from the XML template.
	 * There are no configurable options for Text segments.
	 * 
	 * @param text - The literal text, extracted from in-between segments.
	 */
	public boolean initialize ( String text )
	{
		text_ = text.getBytes();
		if (text_.length == 0)
			return false;
		else
			return true;
	}
	
	/**
	 * Return the literal text to be embedded in the ouput XML.
	 * 
	 * @param context - integer specific to parent block -- ignored.
	 * @return The text String.
	 */
	public String generate ( long context )
	{
		return text_.toString();
	}
	
	/**
	 * Return the literal text to be embedded in the ouput XML.
	 * 
	 * @param context - integer specific to parent block -- ignored.
	 * @return The text String.
	 */
	public long generate ( long context, OutputStream out )
	{
		try { out.write(text_); }
		catch (java.io.IOException e) { e.printStackTrace(); }
		return text_.length;
	}
	
	/**
	 * Determine which kind of segment you have.
	 * @param segmentType An integer representing the enum value for segment type from GenSegment.
	 * @return true if the segment type is GenSegment.LITERAL, false otherwise.
	 */
	public boolean isA ( int segmentType )
	{
		return LITERAL == segmentType;
	}
	
} // end of class GenText
