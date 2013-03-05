/*
 * JEngine Copyright (C) 2001
 *  Jacek Zagorski, Shasta NetWorks LLC
 *  Jeff Gunther, Intalgent Technologies, LLC
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * $Id: HL7Viewer.java,v 1.2.2.1 2002/07/18 19:55:24 alanshields Exp $
 * $Revision: 1.2.2.1 $
 */
package org.jengine.tools.hl7;

import java.util.Properties;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.FileNotFoundException;

import java.util.StringTokenizer;

public class HL7Viewer
{

	private static String openMessage = "Output from ::\n Open Source Java HL7 Viewer\n Copyright (C) 2001\n  Jacek Zagorski, Shasta NetWorks LLC\n  Jeff Gunther, Intalgent Technologies, LLC\n";
	private static String fileName;
	private static String segType;
        private static HL7Utils hl7Util;

	private static final int LEVEL_1 = 1;
	private static final int LEVEL_2 = 2;
	private static final int LEVEL_3 = 3;
	private static final int LEVEL_4 = 4;

	private static int level = 0;
        private static int rep = 0;

        public HL7Viewer (String msg)
        {
            hl7Util = new HL7Utils();
        }
        
	public static void main(String[] args)
	{

		if (args.length != 1)
		{
			System.err.println("USAGE: HL7Viewer filename");
			System.exit(-1);
		}

		fileName = args[0];
		readFile();

	}

	public static void readFile()
	{
		FileInputStream fis;
		InputStreamReader isr;

		StringBuffer line = null;
		char ch;

		line = new StringBuffer();

		try
		{
			fis = new FileInputStream(fileName);
			isr = new InputStreamReader(fis);

			System.out.println(openMessage);
			/*
			 * the os properties should be used to figure out
			 * what kind of return character(s) we're using,
			 * Unix vs Bill
			 */
			System.out.println("Using encoding: "
				+ isr.getEncoding()
				+ ", on system: "
				+ System.getProperty("os.name")
				+ " "
				+ System.getProperty("os.arch")
				+ " "
				+ System.getProperty("os.version")
				+ "\n"
			);

			while (isr.ready())
			{
				ch = (char)isr.read();
				// dumps out the message to stdout character by char
				//System.out.print(ch);
				//System.out.flush();
				if (ch == hl7Util.LINEFEED)
				{
					level = 0;
					hl7Util.decomposeSegments(line.toString());
                                        //decomposeRecursively(line.toString(), CARRIAGERETURN);
				}
				else
					line.append(ch);
			}
			isr.close();
			fis.close();
		}
		catch (IOException ie)
		{
			System.err.println("IOException: " + ie.getMessage());
		}

	}

        /*
         * OLD CODE - Recursion
         *  slick but not flexible enough.
         */
	private static String level1FieldTag = "";
	private static String level2FieldTag = "";
	private static String level3FieldTag = "";

        public static void decomposeRecursively(String str, char delim)
	{

            int i=0;
            StringDelimiter sd;
            
            level++;
            sd = new StringDelimiter(str, delim);
            
            //System.out.println("String has "+sd.countTokens()+" tokens.");
            
            while (sd.hasMoreTokens()) {
                String nt = sd.nextToken();
                
                switch (level) {
                    // Level 1: message decomposed into segments
                    case LEVEL_1:
                        //System.out.println("\t" + nt);
                        if (nt.indexOf(hl7Util.PIPE) >= 0)
                            decomposeRecursively(nt, hl7Util.PIPE);
                        i++;
                        break;
                        
                    case LEVEL_2:
                        if (i == 0) {
                            segType = nt;
                            // for segments other than MSH,
                            // the second field actually begins at 1
                            if (nt.equals("MSH")) i++;
                        }
                        level1FieldTag = segType + "." + i;
                        System.out.println(level1FieldTag + "\t" +  nt);
                        // check for TILDE -- repetition in field
                        if (nt.indexOf(hl7Util.TILDE) >= 0)
                        {
                            if (!hl7Util.isMshDelimiterField(segType, i))
                                decomposeRecursively(nt, hl7Util.TILDE);
                        }
                        else {
                            if (!hl7Util.isMshDelimiterField(segType, i))
                                if (nt.indexOf(hl7Util.CARET) >= 0) decomposeRecursively(nt, hl7Util.CARET);
                            i++;
                            level = LEVEL_2;
                        }
                        break;
                        
                    case LEVEL_3:
                        if (delim == hl7Util.TILDE)
                            level2FieldTag = level1FieldTag + "{" + ++rep + "}." + (i+1);
                        else
                            level2FieldTag = level1FieldTag + "." + (i+1);
                        System.out.println(level2FieldTag + "\t " + nt);
                        if (nt.indexOf(hl7Util.CARET) >= 0) decomposeRecursively(nt, hl7Util.CARET);
                        if (nt.indexOf(hl7Util.AMP) >= 0) decomposeRecursively(nt, hl7Util.AMP);
                        i++;
                        break;
                        
                    case LEVEL_4: 
                        level3FieldTag = level2FieldTag + "." + (i+1);
                        System.out.println(level3FieldTag + "\t " + nt);
                        i++;
                        break;
                        
                }
            }
            level--;

	}

}

