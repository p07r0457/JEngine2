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
 * $Id: LLPWriter.java,v 1.5.2.1 2002/07/18 19:57:20 alanshields Exp $
 * $Revision: 1.5.2.1 $
 */

package org.jengine.tools.network;

import java.io.*;


public class LLPWriter
{
    private OutputStream out;

    public LLPWriter(OutputStream outputstream)
    {
        out = outputstream;
    }

    public void close()
    {
        try
        {
            out.close();
        }
        catch (IOException ie)
        {
            
        }
    }

    public void writeMsg(String s)
        throws IOException
    {
        s = "\013" + s + "\034\r";
        byte abyte0[] = s.getBytes();
        out.write(abyte0);
        out.flush();
    }

}
