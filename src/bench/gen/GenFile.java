/* GenFile.java - Copyright (c) 2004 through 2009, Progress Software Corporation
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

import java.util.*;
import java.io.*;
import java.text.DecimalFormat;

/**
 * Controls generation of XML source based on an input template.
 * <p>The GenFile subsystem generates XML text based on processing instructions embedded
 * in the XML template file. The constructor opens the XML template file and copies it
 * into a char buffer, which is passed to a root GenBLock object for initialization. The
 * writeFile() method then triggers generation of one or more output files, based on the
 * input template. You can also direct output to a String or a generic PrintStream using
 * the generate methods. The main() method can be used as a standalone tool to generate any
 * number of XML files in a batch, and will provide a top-level context, in case you need
 * to distinguish keys in the various files. See com.progress.gen.gen.GenSegment for
 * a description of recognized instructions.
 * </p>
 * <p>You can pass the GenFile object a list of name/value pairs in format "A=X,B=Y,C=Z",
 * that will be copied into the 'variables' table and shared by all child segments.</p>
 * <p>The GenFile class also implements GenSegment, on behalf of the <?gen.file ?> instruction.
 * This supports inclusion of one GenFile file by another.
 * </p>
 * <pre>
 * To invoke programmatically:
 *    GenFile generator = new GenFile();
 *    generator.parseFile("templ.xml");
 *    generator.setVariables("{DATE=10/10/01,AGE=41}");
 *    for (int i=0; i<100; i++)
 *      doc[i] = generator.writeString(i);
 *
 * To invoke Java main:
 * prompt> java com.parelastic.bench.gen.GenFile template=templ.xml out=out/file.xml num=10 vars={DATE=10/10/01,AGE=41}
 *
 * To invoke as a nested GenFile instruction:
 *  &lt;?gen.variables source=DATE=10/10/01,AGE=41 ?&gt;
 *  &lt;?gen.file source=templ.xml ?&gt;
 *
 * Future enhancements:
 *  1. Define a Distribution class to encapsulate sequence options: serial, random, zipf, logN, etc.
 * </pre>
 * @see bench.gen.GenSegment
 * @see bench.gen.GenValue
 * @see bench.gen.GenWords
 * @see bench.gen.GenBlock
 * @see bench.gen.GenText
 * @see bench.gen.GenDate
 * @see bench.gen.GenVariable
 * @author Progress Software Inc., all rights reserved.
 * @version TestHarness8.0
 */
public class GenFile
extends GenSegmentBase
{
	PrintStream logFile_;
	PrintStream fileOut_;
	String      template_;
	GenBlock    rootBlock_;
	
	/**
	 * Simple constructor.
	 */
	public GenFile ( )
	{
		this(System.err, new Properties());
	}
	
	/**
	 * Constructor to set log and variables.
	 * Warning, if any of the variables in the input Properties contain upper-case keys, they will
	 * effectively be ignored, since variables are always converted to lower case.
	 * @param log Output stream for messages.
	 * @param vars Properties file containing initial values for Variables.
	 */
	public GenFile ( PrintStream log, Properties vars )
	{
		super(vars);
		template_ = null;
		logFile_ = log;
		rootBlock_ = new GenBlock(variables_);
	}
	
	/**
	 * Initialize a GenFile segment with input parameters.
	 * These calls are made by the GenSegmentBase.initialize method to allow the subclass to decode
	 * class-specific parameters.  Local attribute values for this subclass include:
	 * <pre>
	 *   source=file - read word list from a file (default is to generate 5 character garbage words
	 * </pre>
	 *
	 * @param key An attribute name recognized by the segment type.
	 * @param val The value of that attribute.
	 * @return true if the name/value pair can be decoded, false otherwise.
	 */
	public boolean decode ( String key, String val )
	{
		if (key.equals("source"))
		{
			template_ = val;
			return true;
		}
		else
			return false;
	}
	
	/** Verify values and parse template file.
	 * This callback is invoked only when the GenFile object is created because of a gen.file instruction
	 * embedded in another template file.
	 * @return true if a valid template exists, false if template is missing or infinite recursion is detected.
	 */
	protected boolean validate ( )
	{
		if (template_ == null || nestingLevel__++ > 25)  // if more than 25 levels of file include, we've got problems...
		{
			System.err.println("GenFile: template missing or nested more than 25 deep");
			return false;
		}
		parseFile(template_);  //note returned file size of zero is not an error
		return true;
	}
	
	/**
	 * Parse input template String and initialize all GenSegments.
	 *
	 * @param template Input template string with embedded processing instructions.
	 */
	public void parseString ( String template )
	{
		rootBlock_.initialize("count=1 context=combined");  // One block, include parent # in sub-contexts
		rootBlock_.parse(template, 0);  // Root block processes entire template file
	}
	
	/**
	 * Read input template file and set up generator.
	 *
	 * @param templateFile File path for template file with embedded processing instructions.
	 * @return Number of bytes read from template file.
	 */
	public int parseFile ( String templateFile )
	{
		StringBuffer buffer = new StringBuffer(BUFFER_SIZE);
		try
		{
			BufferedReader in = new BufferedReader(new FileReader(templateFile));
			char[] cbuf = new char[CBUF_SIZE];
			int charsRead;
			int totalSize = 0;
			while ((charsRead = in.read(cbuf,0,CBUF_SIZE)) >= 0)
			{
				totalSize += charsRead;
				if (totalSize > 99999999) {
					in.close();
					throw new RuntimeException("GenFile: Template buffer too big.");
				}
				buffer.append(cbuf,0,charsRead);
			}
			in.close();
			String template = buffer.toString();
			parseString(template);
			return totalSize;
		}
		catch (java.io.FileNotFoundException e)
		{
			throw new RuntimeException("GenFile: Template file not found:" + templateFile);
		}
		catch (java.io.IOException e)
		{
			throw new RuntimeException("GenFile: Template file read error.");
		}
	}
	
	/**
	 * Return generated XML as a String.
	 * This routine delegates generation to the top-level block node.
	 *
	 * @param context a unique identifying number used to generate key values.
	 * @return A long String containing generated XML.
	 */
	public String generate ( long context )
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream(BUFFER_SIZE);
		rootBlock_.generate(context, out);
		return out.toString();
	}

	/**
	 * Return generated XML as a String.
	 * This routine delegates generation to the top-level block node.
	 *
	 * @param context a unique identifying number used to generate key values.
	 * @return A long String containing generated XML.
	 * @throws IOException 
	 */
	public void generateStream ( long context, OutputStream out ) throws IOException
	{
		rootBlock_.generate(context, out);
	}

	/**
	 * Generate XML into an output stream.
	 * This routine delegates generation to the top-level block node. Since internal segment
	 * structures are maintained as byte arrays, this is much more efficient than the String
	 * generate. This form of generate may be explicitly invoked by a program, is used by
	 * the main method of this class, and is called when a gen.file instruction specifies
	 * a nested GenFile template within another one.
	 *
	 * @param context a unique identifying number used to generate key values.
	 * @param out The destination stream for the generated XML.
	 * @return Byte count of generated output if successful, -1 otherwise.
	 */
	public long generate ( long context, OutputStream out )
	{
		long subContext = transformLong(context);  // adjust context value to specified number range
		try {
			return rootBlock_.generate(subContext, out);
		} catch (Exception e) {
			return -1;
		}
	}
	
	/**
	 * Initialize output file and generate output into it.
	 * Normally the calling program generates context input as sequential integers.
	 * NOTE: this should probably be replaced by generate(long,File).
	 *
	 * @param context a unique identifying number used to generate key values.
	 * @param outFilePath the fully-qualified dir/file name to create as output.
	 */
	public long writeFile ( long context, String outFilePath )
	throws java.io.FileNotFoundException
	{
		fileOut_ = new PrintStream(new FileOutputStream(outFilePath));
		long count = rootBlock_.generate(context, fileOut_);
		fileOut_.close();
		return count;
	}
	
	/**
	 * Determine which kind of segment you have.
	 * @param segmentType An integer representing the enum value for segment type from GenSegment.
	 * @return true if the segment type is GenSegment.GENXML, false otherwise.
	 */
	public boolean isA ( int segmentType )
	{
		return GENXML == segmentType;
	}
	
	// ================= Static variables and methods ===========================
	public static final String DFLT_TEMPLATE_FILE = "template.gen";
	public static final String DFLT_OUTPUT_PATH = ".\\data\\file.xml";
	public static final String DFLT_DECIMAL_FORMAT = "0000000000000000";
	static final int BUFFER_SIZE = 32000;  // Starting size for xml output buffer
	static final int CBUF_SIZE = 4096;    // Buffer size for template file reads
	static final int DFLT_NUM_DOCS = 1;
	static final int DFLT_START_NUM = 0;
	
	static private int nestingLevel__ = 0;  // used to detect when a gen.file instruction includes itself
	
	/** Print usage information for GenFile main(). */
	public static void printHelp ( )
	{
		System.out.println("java com.parelastic.bench.gen.GenFile [num=N] [start=S] [template=T] [out=O] [format=F] [vars=V] [-help | /?]");
		System.out.println("\n\tWhere:");
		System.out.println("\t  -help or /? or incorrect syntax - print this help text");
		System.out.println("\t  N=NUM_DOCS - number of output files to generate (dflt 1)");
		System.out.println("\t  S=START_NO - starting number for document name and internal template context (dflt 0)");
		System.out.println("\t  T=TEMPLATE - input template file used to generate text (dflt template.gen)");
		System.out.println("\t  O=OUTPUT_FILE - output file path (dflt ./data/file.xml)");
		System.out.println("\t  F=NAME_FORMAT - a valid DecimalFormat string used in the name (dflt zero-padded to number of significant digits)");
		System.out.println("\t  V=VARIABLES - a list of name=value pairs used to initialize variables, either a Java properties file");
		System.out.println("\t            or an explicit list in the format: {var1=x,var2=y,...} (no whitespace).");
		System.out.println("\n\n\t - If NUM_DOCS == 1, output file is named OUTPUT_FILE.");
		System.out.println("\t - Otherwise OUT_FILE is created by appending a generated integer prior to any suffix in the OUTPUT_FILE string.");
		System.out.println("\tFor example:");
		System.out.println("\t  java com.parelastic.bench.gen.GenFile num=1 template=inTmpl.gen out=out.txt  ==> output file out.txt");
		System.out.println("\t  java com.parelastic.bench.gen.GenFile num=3 template=inTmpl.gen out=dir/out  ==> output files dir/out1, dir/out2 and dir/out3");
		System.out.println("\t  java com.parelastic.bench.gen.GenFile num=2 start=10 template=inTemplate out=out.xml  ==> output files out10.xml and out11.xml");
		System.out.println("\t  java com.parelastic.bench.gen.GenFile num=2 format=000  ==> output files data/file0000.xml and data/file0001.xml");
	}
	
	/**
	 * Invoke GenFile to generate XML data files.
	 *
	 * @see #printHelp
	 **/
	public static void main ( String[] args )
	{
		System.out.println("GenFile");
		int numDocs = DFLT_NUM_DOCS;
		int startNum = DFLT_START_NUM;
		String templateFile = DFLT_TEMPLATE_FILE;
		String outputPath = DFLT_OUTPUT_PATH;
		String format = null;
		String variables = null;
		try
		{
			for (int i = 0; i < args.length; i++)
			{
				if (args[i].equals("/?") || args[i].equalsIgnoreCase("-help"))
				{
					printHelp();
					return;
				}
				else if (args[i].startsWith("num="))
				{
					numDocs = Integer.parseInt(args[i].substring(args[i].indexOf("num=")+4));
				}
				else if (args[i].startsWith("start="))
				{
					startNum = Integer.parseInt(args[i].substring(args[i].indexOf("start=")+6));
				}
				else if (args[i].startsWith("template="))
				{
					templateFile = args[i].substring(args[i].indexOf("template=")+9);
				}
				else if (args[i].startsWith("out="))
				{
					outputPath = args[i].substring(args[i].indexOf("out=")+4);
				}
				else if (args[i].startsWith("format="))
				{
					format = args[i].substring(args[i].indexOf("format=")+7);
				}
				else if (args[i].startsWith("vars="))
				{
					variables = args[i].substring(args[i].indexOf("vars=")+5);
				}
				else
				{
					printHelp();
					return;
				}
			}
		}
		catch (NumberFormatException e)
		{
			System.out.println("GenFile: Invalid num= or start= arg");
			printHelp();
			return;
		}
		if (format == null)
			format = DFLT_DECIMAL_FORMAT.substring(0, Integer.toString(numDocs+startNum-1).length());
		DecimalFormat numberFormat = new DecimalFormat(format);
		
		GenFile theTest = new GenFile();
		if (variables != null)
			theTest.setVariables(variables); // reset variables, if specified
		theTest.parseFile(templateFile); // set up document generator with correct params
		
		for (int docNum = startNum; docNum < startNum+numDocs; docNum++)  // generate each Xml file
		{
			String outFilePath;
			if (docNum == 0  && numDocs == 1)
				outFilePath = outputPath;
			else
			{
				int lastDot = outputPath.lastIndexOf(".");
				if (lastDot > 0)
					outFilePath = new String(outputPath.substring(0,lastDot) + numberFormat.format(docNum) + outputPath.substring(lastDot));
				else
					outFilePath = new String(outputPath + numberFormat.format(docNum));
			}
			try
			{
				System.out.println("Writing file " + outFilePath);
				theTest.writeFile(docNum, outFilePath);
			}
			catch (java.io.FileNotFoundException e)
			{
				System.out.println("\tGenFile: Error creating file " + outFilePath);
				return;
			}
			catch (RuntimeException e)
			{
				System.err.println(e);
				e.printStackTrace(System.err);
			}
		}
		System.out.println("Done.");
	}
	
} // end of class GenFile
