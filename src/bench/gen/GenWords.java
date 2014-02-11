/* GenWords.java - Copyright (c) 2004 through 2008, Progress Software Corporation.
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

import java.io.ByteArrayOutputStream;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Vector;
import java.io.File;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.Reader;
import java.io.FileInputStream;
import java.io.StreamTokenizer;

/**
 * Generates text as a series of random words, in String format.
 *   You configure the word generator with parameters of the processing instruction,
 * which are passed to the initialize() method.  Instructions are in the following format:
 * <pre>
 *   &lt?;gen.words min=N max=M order=OOO source=SSS save=VVV ?&gt;
 *      where N-M defines a range for the number of words to insert (default 1-100);
 *         alternatively, use count=N to specify a constant number of words per entry
 *      OOO defines the mechanism for selecting words from the list:
 *         order=zipf (default) word selection distributed in descending order of frequency
 *         order=serial sequentially go through list, then restart at beginning
 *         order=random select randomly from the list with equal probability
 *      SSS indicates the source for the words:
 *        source={word1,word2,word3} embeds list of comma-delimited words in the param (NOTE: embedded spaces not supported)
 *        source=SSS is the name of a text file containing words or phrases
 *        default is to generate nonsense words with average length=WORD_SIZE
 *      and VVV is a new variable name where the current value will be stored for use by GenVariable.
 * </pre>
 *    If the source name is an existing file the word list will be read from that file,
 * otherwise a new word list will be randomly generated and given that name.  Once the
 * word list is established, generate() will always select words randomly.
 * </p>
 * <pre>
 * Future enhancements:
 *  1. Allow some control over generated word lists, e.g. average length & frequency
 *  2. Allow source filenames to be relative to path of original GenXml template file, instead of current dir.
 *  3. Add a start param to specify that words prior to that on the list should be skipped.
 * </pre>
 * @see bench.gen.GenSegment
 * @see bench.gen.ZipfCreator
 * @author Progress Software Inc., all rights reserved.
 * @version TestHarness8.0
 */
public class GenWords
extends GenSegmentBase
{
	boolean randomCount_; // Number of words vary randomly between min_ and max_
	int     wordnum_;     // Current word number within list (used if dist_ == SERIAL)
	String source_;       // from "source=" input param
	Vector<byte[]> wordList_;     // an element of wordLists__
	int    listSize_;     // number of random words in the list
	private ByteArrayOutputStream buffer_; // reused word output buffer
	
	/**
	 * Simple constructor - real setup is done by initialize().
	 */
	public GenWords ( Properties variables )
	{
		super(variables);
		randomCount_ = false;  // By default, specify exact count of inserted blocks
		max_ = (long) DFLT_COUNT; // Override default max count
		dist_ = ZIPF; // Override default distribution, select words based on Zipf distribution
		wordnum_ = 1;
		source_ = DEFAULT_WORD_LIST;  // If this file doesn't exist, a random table is created
	}
	
	/**
	 * Initialize the segment with input parameters.
	 * The caller should pass in the attributes of the embedded processing instruction
	 * from the XML template.  Parameters specific to this class include:
	 * <pre>
	 *     source=file - read word list from a file (default is to generate 5 character garbage words)
	 * </pre>
	 * @see bench.gen.GenSegmentBase#decode
	 * @param key An attribute name recognized by the segment type.
	 * @param val The value of that attribute.
	 * @return true if the name/value pair can be decoded, false otherwise.
	 */
	protected boolean decode ( String key, String val )
	{
		if (key.equals("source"))
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
		wordList_ = (Vector<byte[]>) wordLists__.get(source_);  // shared static cache of word lists
		if (wordList_ == null)
		{
			if (source_.startsWith("{"))
			{
				wordList_ = parseWordsFromString(source_);
			}
			else
			{
				File wordFile = new File(source_);
				if (wordFile.exists() && wordFile.isFile())
				{
					wordList_ = readWordsFromFile(wordFile);
				}
				else
				{
					wordList_ = generateWordList();
				}
			}
			wordLists__.put(source_, wordList_);
		} // assume at this point wordList exists and is correct
		listSize_ = wordList_.size();
		return true;
	}
	
	/** Implement zipf limit as max number of words in word list. */
	protected int zipfLimit ( )
	{
		return listSize_;
	}
	
	/**
	 * Generate string of word text based on template specifications.
	 *
	 * @param context An integer distinguishing this iteration of the block.
	 * @return The formatted String representing the generated string of words.
	 */
	public String generate ( long context )
	{
		buffer_ = new ByteArrayOutputStream((int) max_ * WORD_SIZE); // Maybe re-use the buffer
		int numWords = (int) transformLong(randomLong());
		for (int i=0; i < numWords; i++)
		{
			int wordNum = wordnum_ % listSize_;
			if (dist_ == RANDOM)
				wordNum = randomInt(listSize_);
			else if (dist_ == ZIPF)
				wordNum = zipfInt() % listSize_;
			else if (dist_ == CONTEXT)
				wordNum = (int) context % listSize_;
			try { buffer_.write(wordList_.elementAt(wordNum)); }
			catch (java.io.IOException e) { e.printStackTrace(); }
			wordnum_++;
			if (i < max_ - 1) // don't add separator for the last word
			{
				if (i % 14 == 13)
					buffer_.write('\n');   // any reason to make num words per line configurable?
				else
					buffer_.write(' ');   // any reason to make num words per line configurable?
			}
		}
		String returnVal = buffer_.toString();
		if (isSavingVar_)
			setVariable(saveVar_, returnVal);  // update value of global Variable.
		return returnVal;
	}
	
	/**
	 * Determine which kind of segment you have.
	 * @param segmentType An integer representing the enum value for segment type from GenSegment.
	 * @return true if the segment type is GenSegment.WORDS, false otherwise.
	 */
	public boolean isA ( int segmentType )
	{
		return WORDS == segmentType;
	}
	
	/**
	 * Build word list based on existing input file.
	 * The standard Java tokenizer class is used to parse individual words; to be safe, it is
	 * best to put words one per line, but other whitespace delimiters are o.k. This has been
	 * shown to work with Korean text, but you may need to tweak the tokenizer delimiters for
	 * non-English text.
	 *
	 * @param file The text file to read the word list from.
	 * @return A Vector containing the words read from the file.
	 */
	public Vector<byte[]> readWordsFromFile ( File file )
	{
		StreamTokenizer tokens;
		try
		{
			Reader r = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
			tokens = new StreamTokenizer(r);
			tokens.whitespaceChars('\n', '\n');
			tokens.whitespaceChars('\f', '\f');
			tokens.commentChar('#');
			tokens.slashSlashComments(true);
			tokens.slashStarComments(true);
		}
		catch (java.io.FileNotFoundException e)
		{
			return generateWordList();
		}
		
		Vector<byte[]> wordList = new Vector<byte[]>((int)file.length() / WORD_SIZE); // estimate list size
		try
		{
			while (tokens.nextToken() != StreamTokenizer.TT_EOF)
			{
				if (tokens.ttype == StreamTokenizer.TT_WORD || tokens.ttype == '\'' || tokens.ttype == '"')
				{
					wordList.addElement(tokens.sval.getBytes());  // append word to list
				}
			}
		}
		catch (java.io.IOException e)
		{
			System.err.println("GenWords error: Cannot parse words file " + file.getAbsolutePath() + ", generating random words instead.");
			wordList = generateWordList();
		}
		return wordList;
	}
	/**
	 * Build word list based on list of words embedded directly in the source parameter.
	 * This is equivalent to an enum capability. The input string must begin with a "{"
	 * character, and should end with a "}". Individual words are delimited by commas,
	 * and cannot contain embedded spaces.
	 * @param source A string containing the words of the list in format {word1,word2,word3}
	 * @return a Vector of Strings containing the words.
	 */
	public Vector<byte[]> parseWordsFromString ( String source )
	{
		int begin = 1;  // token begins with '{'
		int end = source.length();
		if (source.endsWith("}"))
			end--;
		else
			System.err.println("GenWords: illegal syntax: source=" + source);
		Vector<byte[]> wordlist = new Vector<byte[]>();
		while (begin < end)
		{
			int comma = source.indexOf(",", begin);
			if (comma < begin)
			{
				
				wordlist.addElement(source.substring(begin, end).getBytes());
				begin = end;
			}
			else
			{
				wordlist.addElement(source.substring(begin, comma).getBytes());
				begin = comma + 1;
			}
		}
		return wordlist;
	}
	
	/**
	 * Build word list based on randomly generated strings.
	 * The character set used here is taken from the static constant field CHARACTER_LIST. You may
	 * want to modify that variable for non-English applications.
	 *
	 * @return A Vector containing words with randomly generated characters.
	 */
	public Vector<byte[]> generateWordList ( )
	{
		Vector<byte[]> wordList = new Vector<byte[]>(LIST_SIZE);     // fixed list size
		for (int i=0; i < LIST_SIZE; i++)
		{
			int wordLength = 1 + randomInt(WORD_SIZE) + randomInt(WORD_SIZE); // size range 1 to 2*n-1, avg=n
			byte[] wordBuf = new byte[wordLength];
			for (int j=0; j < wordLength; j++)
			{
				wordBuf[j] =CHARACTER_LIST[Math.abs(randomInt(CHARACTER_LIST.length))];
			}
			wordList.addElement(wordBuf);  // append word to list
		}
		return wordList;
	}
	
	// =================== static fields and methods =======================
	
	protected static final String DEFAULT_WORD_LIST = "WordList.txt";
	
	protected static Hashtable<String,Vector<byte[]>> wordLists__ = new Hashtable<String,Vector<byte[]>>(); // contains word lists, stored as Vectors
	protected static final int LIST_SIZE = 1000; // all generated lists are this size
	protected static final int WORD_SIZE = 7;  // avg generated words are this length
	protected static final int DFLT_COUNT = 100;
	protected static final byte[] CHARACTER_LIST = "abcdefghijklmnopqrstuvwxyz".getBytes();
	
} // end of class GenWords

