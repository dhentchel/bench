/* GenDate.java - Copyright (c) 2004 through 2008, Progress Software Corporation.
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
import java.util.StringTokenizer;
import java.util.Calendar;
import java.text.DecimalFormat;

/**
 * Generates variable date values in String format.
 *   You configure the date generator with parameters of the processing instruction,
 * which are passed to the initialize() method.  Instructions are in the following format:
 * <pre>&lt?gen.date order=XXX increment=II type=TTT start=MM/DD/YYYY time=99:99:99 save=VVV ?&gt</p>
 *    where XXX can be serial or context (default),
 *    II is 0 (igmore) or a valid integer to be added to parent context,
 *    TTT is a type of datetime element, can be oagi, ymd, ymdh, mdy (default) or ymdt
 *    MM/DD/YYYY is a start date (default "01/01/2000"),
 *    99:99:99 is a time stamp using 24 hr clock (default "00:00:00")
 *    VVV is a new variable name where the current value will be stored for use by GenVariable.
 * Note that the individual elements of the date or time can be expressed as Variable values, e.g.
 *    start=10/15/$YEAR time=$ID:$SUB:17
 * where YEAR, ID and SUB are defined GenVariable's.
 * </pre>
 * @author Progress Software Inc., all rights reserved.
 * @version TestHarness8.0
 */
public class GenDate
extends GenSegmentBase
{
	long   count_;                    // Used for SERIAL only
	long   type_;                     // Date type (default is MDY)
	String startDate_;
	String timeStamp_;
	
	/**
	 * Simple constructor sets defaults - real setup is done by initialize().
	 */
	public GenDate ( Properties variables )
	{
		super(variables);
		count_ = 0;
		type_ = MDY;
		startDate_ = DFLT_DATE;
		timeStamp_ = DFLT_TIME;
		isSavingVar_ = false;
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
		if (key.equals("type"))
		{
			if ("oagi".equalsIgnoreCase(val))
				type_ = OAGI;
			else if ("mdy".equalsIgnoreCase(val))
				type_ = MDY;
			else if ("ymd".equalsIgnoreCase(val))
				type_ = YMD;
			else if ("ymdh".equalsIgnoreCase(val))
				type_ = YMD_HYPHEN;
			else if ("ymdt".equalsIgnoreCase(val))
				type_ = YMDT;
			else System.err.println("Invalid date format: " + val);
		}
		else if (key.equals("start"))
		{
			try
			{
				int amonth = (getIntParam(val.substring(0,val.indexOf("/")),1) - 1) % 12 +1 ;
				int aday = (getIntParam(val.substring(val.indexOf("/") + 1, val.lastIndexOf("/")),1) - 1) % 31 +1 ;
				int ayear = getIntParam(val.substring(val.lastIndexOf("/") + 1),1950);
				startDate_ = amonth + "/" +  aday + "/" + ayear;
			}
			catch (Exception e) { startDate_ = DFLT_DATE; }
		}
		else if (key.equals("time"))
		{
			try
			{
				int ahour = (getIntParam(val.substring(0,val.indexOf(":")),10) - 1) % 24 +1 ;
				int aminute = (getIntParam(val.substring(val.indexOf(":") + 1, val.lastIndexOf(":")),20) - 1) % 60 +1 ;
				int asecond = (getIntParam(val.substring(val.lastIndexOf(":") + 1),30) - 1) % 60 +1 ;
				timeStamp_ = ahour + ":" +  aminute + ":" + asecond;
			}
			catch (Exception e) { startDate_ = DFLT_DATE; }
		}
		else
			return false;
		return true;
	}
	
	/**
	 * Generate a key value based on template specifications.
	 *
	 * @param context - an integer distinguishing this iteration of the block.
	 * @return The formatted String representing the generated numeric value.
	 */
	public String generate ( long context )
	{
		long nextLong = context; // dist_ == CONTEXT becomes the default
		if (dist_ == SERIAL)
			nextLong = count_;
		else if (dist_ == RANDOM)
			nextLong = randomLong();
		else if (dist_ == ZIPF)
			nextLong = zipfLong();
		long dayIncrement = transformLong(nextLong);
		count_++;
		
		// add value to Calendar and return theDate
		Calendar cal = Calendar.getInstance();
		cal = StringToCalendar(startDate_, timeStamp_);
		cal.add(Calendar.DATE, (int) dayIncrement);
		String theDate = null;
		if (type_ == OAGI)
		{
			// convert cal to OAGi elements and store in theDate
			theDate = CalendarToOAGi(cal);
		}
		else if (type_ == YMD)
		{
			// convert cal to YYYYMMDD and store in theDate
			theDate = CalendarToYMD(cal, false);
		}
		else if (type_ == YMD_HYPHEN)
		{
			// convert cal to YYYYMMDD and store in theDate
			theDate = CalendarToYMD(cal, true);
		}
		else if (type_ == YMDT)
		{
			// convert cal to YYYYMMDD and store in theDate
			theDate = CalendarToYMDT(cal);
		}
		else
		{
			// convert cal to MM/DD/YYYY and store in theDate
			theDate = CalendarToString(cal);
		}
		cal = null;
		if (isSavingVar_)
			setVariable(saveVar_, theDate);  // update value of global Variable.
		return theDate;
	}
	
	/**
	 * This takes a string in the form of MM/DD/YYYY and a string in the form of
	 * 99:99:99 and converts it to Calendar.
	 *
	 * @param mmddyyyy the date in MM/DD/YYYY string format
	 * @param time the time in 99:99:99 string format
	 * @return cal the Calendar representation of mmddyyyy
	 */
	private Calendar StringToCalendar ( String mmddyyyy, String time )
	{
		Calendar cal = Calendar.getInstance();
		cal = DateStringToCalendar(mmddyyyy, cal);
		cal = TimeStringToCalendar(time, cal);
		cal.set(Calendar.MILLISECOND, 0);
		
		return cal;
	}
	
	/**
	 * This takes a string in the form of MM/DD/YYYY and converts it to Calendar.
	 *
	 * @param mmddyyyy the date in MM/DD/YYYY string format
	 * @param cal the Calendar passed in
	 * @return cal the Calendar representation of mmddyyyyy
	 */
	private Calendar DateStringToCalendar ( String mmddyyyy, Calendar cal )
	{
		StringTokenizer st = new StringTokenizer(mmddyyyy, DATE_SEPARATOR);
		int i = 0;
		int iToken = 0;
		while (st.hasMoreTokens())
		{
			i++;
			iToken = Integer.parseInt(st.nextToken());
			if (i == 1)
			{
				cal.set(Calendar.MONTH, iToken - 1);  // note that the month is zero-based
			}
			else if (i == 2)
			{
				cal.set(Calendar.DAY_OF_MONTH, iToken);
			}
			else if (i == 3)
			{
				cal.set(Calendar.YEAR, iToken);
			}
			else
				break;   // ignore other stuff
		}
		return cal;
	}
	
	/**
	 * This takes a string in the form of 99:99:99 and converst it to Calendar.
	 *
	 * @param time the time in 99:99:99 string format
	 * @param cal the Calendar passed in
	 * @return cal the Calendar representation of 99:99:99
	 */
	private Calendar TimeStringToCalendar ( String time, Calendar cal )
	{
		StringTokenizer st = new StringTokenizer(time, TIME_SEPARATOR);
		int i = 0;
		int iToken = 0;
		while (st.hasMoreTokens())
		{
			i++;
			iToken = Integer.parseInt(st.nextToken());
			if (i == 1)
			{
				cal.set(Calendar.HOUR_OF_DAY, iToken);
			}
			else if (i == 2)
			{
				cal.set(Calendar.MINUTE, iToken);
			}
			else if (i == 3)
			{
				cal.set(Calendar.SECOND, iToken);
			}
			else
				break;   // ignore other stuff
		}
		return cal;
	}
	
	/**
	 * Take a Calendar and return MM/DD/YYYY
	 * This format does not zero-fill single-digit days or months.
	 * @param cal the input Calendar
	 * @return The calendar date in MM/DD/YYYY format (no zero-fill).
	 */
	private String CalendarToString ( Calendar cal )
	{
		StringBuffer mmddyyyy = new StringBuffer();
		if (cal != null)
		{
			mmddyyyy.append(String.valueOf(cal.get(Calendar.MONTH)+1));
			mmddyyyy.append(DATE_SEPARATOR);
			mmddyyyy.append(String.valueOf(cal.get(Calendar.DAY_OF_MONTH)));
			mmddyyyy.append(DATE_SEPARATOR);
			mmddyyyy.append(String.valueOf(cal.get(Calendar.YEAR)));
		}
		return mmddyyyy.toString();
	}
	
	/**
	 * Take a Calendar and return YYYY-MM-DD.
	 * This format will zero-fill days and months
	 * @param cal the input Calendar.
	 * @return The calendar date in YYYYMMDD format with no separator.
	 */
	private String CalendarToYMD ( Calendar cal, boolean useHyphen )
	{
		StringBuffer yyyymmdd = new StringBuffer();
		if (cal != null) {
			yyyymmdd.append(String.valueOf(cal.get(Calendar.YEAR)));
			if (useHyphen) yyyymmdd.append("-");
			yyyymmdd.append(TWO_DIGIT_FMT.format(cal.get(Calendar.MONTH)+1));
			if (useHyphen) yyyymmdd.append("-");
			yyyymmdd.append(TWO_DIGIT_FMT.format(cal.get(Calendar.DAY_OF_MONTH)));
		}
		return yyyymmdd.toString();
	}
	
	/**
	 * Take a Calendar and return YYYY-MM-DDTHH:MM:SS.
	 * This format will zero-fill days, months, hours, minutes and seconds.
	 * @param cal the input Calendar.
	 * @return The calendar date in YYYYMMDD format with no separator.
	 */
	private String CalendarToYMDT ( Calendar cal )
	{
		StringBuffer val = new StringBuffer();
		if (cal != null)
		{
			val.append(String.valueOf(cal.get(Calendar.YEAR)));
			val.append("-");
			val.append(TWO_DIGIT_FMT.format(cal.get(Calendar.MONTH)+1));
			val.append("-");
			val.append(TWO_DIGIT_FMT.format(cal.get(Calendar.DAY_OF_MONTH)));
			val.append("T");
			val.append(TWO_DIGIT_FMT.format(cal.get(Calendar.HOUR_OF_DAY)));
			val.append(":");
			val.append(TWO_DIGIT_FMT.format(cal.get(Calendar.MINUTE)));
			val.append(":");
			val.append(TWO_DIGIT_FMT.format(cal.get(Calendar.SECOND)));
		}
		return val.toString();
	}
	
	/** Take a Calendar and return all OAGi elements
	 * This format will zero-fill all elements.
	 * @param cal the input Calendar
	 * @return datetimeOAGi all OAGi DATETIME subelements as a String
	 */
	private String CalendarToOAGi ( Calendar cal )
	{
		StringBuffer datetimeOAGi = new StringBuffer();
		if (cal != null)
		{
			datetimeOAGi.append(CalendarToOAGiYear(cal));
			datetimeOAGi.append(CalendarToOAGiMonth(cal));
			datetimeOAGi.append(CalendarToOAGiDay(cal));
			datetimeOAGi.append(CalendarToOAGiHour(cal));
			datetimeOAGi.append(CalendarToOAGiMinute(cal));
			datetimeOAGi.append(CalendarToOAGiSecond(cal));
			datetimeOAGi.append(CalendarToOAGiSubSecond(cal));
			datetimeOAGi.append(CalendarToOAGiTimezone(cal));
		}
		return datetimeOAGi.toString();
	}
	
	/**
	 * Take a Calendar and return OAGi YEAR
	 *
	 * @param cal the input Calendar
	 * @return yearOAGi YEAR element in OAGi as a String
	 */
	private String CalendarToOAGiYear ( Calendar cal )
	{
		StringBuffer yearOAGi = new StringBuffer();
		if (cal != null)
		{
			yearOAGi.append("<YEAR>");
			yearOAGi.append(String.valueOf(cal.get(Calendar.YEAR)));
			yearOAGi.append("</YEAR>");
		}
		return yearOAGi.toString();
	}
	
	/**
	 * Take a Calendar and return OAGi MONTH
	 *
	 * @param cal the input Calendar
	 * @return monthOAGi MONTH element in OAGi as a String
	 */
	private String CalendarToOAGiMonth ( Calendar cal )
	{
		StringBuffer monthOAGi = new StringBuffer();
		if (cal != null)
		{
			monthOAGi.append("<MONTH>");
			monthOAGi.append(TWO_DIGIT_FMT.format(cal.get(Calendar.MONTH)+1));
			monthOAGi.append("</MONTH>");
		}
		return monthOAGi.toString();
	}
	
	/**
	 * Take a Calendar and return OAGi DAY
	 *
	 * @param cal the input Calendar
	 * @return dayOAGi DAY element in OAGi as a String
	 */
	private String CalendarToOAGiDay ( Calendar cal )
	{
		StringBuffer dayOAGi = new StringBuffer();
		if (cal != null)
		{
			dayOAGi.append("<DAY>");
			dayOAGi.append(TWO_DIGIT_FMT.format(cal.get(Calendar.DAY_OF_MONTH)));
			dayOAGi.append("</DAY>");
		}
		return dayOAGi.toString();
	}
	
	/**
	 * Take a Calendar and return OAGi HOUR
	 *
	 * @param cal the input Calendar
	 * @return hourOAGi HOUR element in OAGi as a String
	 */
	private String CalendarToOAGiHour ( Calendar cal )
	{
		StringBuffer hourOAGi = new StringBuffer();
		if (cal != null)
		{
			hourOAGi.append("<HOUR>");
			hourOAGi.append(TWO_DIGIT_FMT.format(cal.get(Calendar.HOUR_OF_DAY)));
			hourOAGi.append("</HOUR>");
		}
		return hourOAGi.toString();
	}
	
	/**
	 * Take a Calendar and return OAGi MINUTE
	 *
	 * @param cal the input Calendar
	 * @return minuteOAGi MINUTE element in OAGi as a String
	 */
	private String CalendarToOAGiMinute ( Calendar cal )
	{
		StringBuffer minuteOAGi = new StringBuffer();
		if (cal != null)
		{
			minuteOAGi.append("<MINUTE>");
			minuteOAGi.append(TWO_DIGIT_FMT.format(cal.get(Calendar.MINUTE)));
			minuteOAGi.append("</MINUTE>");
		}
		return minuteOAGi.toString();
	}
	
	/**
	 * Take a Calendar and return OAGi SECOND
	 *
	 * @param cal the input Calendar
	 * @return secondOAGi SECOND element in OAGi as a String
	 */
	private String CalendarToOAGiSecond ( Calendar cal )
	{
		StringBuffer secondOAGi = new StringBuffer();
		if (cal != null)
		{
			secondOAGi.append("<SECOND>");
			secondOAGi.append(TWO_DIGIT_FMT.format(cal.get(Calendar.SECOND)));
			secondOAGi.append("</SECOND>");
		}
		return secondOAGi.toString();
	}
	
	/**
	 * Take a Calendar and return OAGi SUBSECOND
	 *
	 * @param cal the input Calendar
	 * @return subSecondOAGi SUBSECOND in OAGi as a String
	 */
	private String CalendarToOAGiSubSecond ( Calendar cal )
	{
		StringBuffer subSecondOAGi = new StringBuffer();
		if (cal != null)
		{
			subSecondOAGi.append("<SUBSECOND>");
			subSecondOAGi.append(FOUR_DIGIT_FMT.format(cal.get(Calendar.MILLISECOND)));
			subSecondOAGi.append("</SUBSECOND>");
		}
		return subSecondOAGi.toString();
	}
	
	/**
	 * Take a Calendar and return OAGi TIMEZONE
	 *
	 * @param cal the input Calendar
	 * @return timezoneOAGi TIMEZONE in OAGi as a String
	 */
	private String CalendarToOAGiTimezone ( Calendar cal )
	{
		StringBuffer timezoneOAGi = new StringBuffer();
		if (cal != null)
		{
			timezoneOAGi.append("<TIMEZONE>");
			timezoneOAGi.append(String.valueOf(cal.get(Calendar.ZONE_OFFSET)/36000));
			timezoneOAGi.append("</TIMEZONE>");
		}
		return timezoneOAGi.toString();
	}
	
	/**
	 * Determine which kind of segment you have.
	 * @param segmentType An integer representing the enum value for segment type from GenSegment.
	 * @return true if the segment type is GenSegment.DATE, false otherwise.
	 */
	public boolean isA ( int segmentType )
	{
		return DATE == segmentType;
	}
	
	// static fields and methods
	protected static final int OAGI = 4;      // Date type will be OAGi
	protected static final int MDY = 5;       // Date type will be MM/DD/YYYY
	protected static final int YMD = 6;       // Date type will be YYYYMMDD
	protected static final int YMDT = 7;       // Date type will be YYYYDDMMT00:00:00
	protected static final int YMD_HYPHEN = 8; // Date type will be YYYY-MM-DD
	protected static final String DATE_SEPARATOR = "/";
	protected static final String TIME_SEPARATOR = ":";
	protected static final String DFLT_DATE = "01/01/2000";
	protected static final String DFLT_TIME = "00:00:00";
	protected static DecimalFormat TWO_DIGIT_FMT = new DecimalFormat("00");
	protected static DecimalFormat FOUR_DIGIT_FMT = new DecimalFormat("0000");
	
} // end of class GenDate
