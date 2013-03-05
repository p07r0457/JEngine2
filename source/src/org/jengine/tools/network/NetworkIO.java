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
 * $Id: NetworkIO.java,v 1.1.2.1 2002/07/18 19:57:20 alanshields Exp $
 * $Revision: 1.1.2.1 $
 */

package org.jengine.tools.network;

//import org.jboss.web.*;

import java.io.*;
import java.net.*;
import org.apache.log4j.Category;

public class NetworkIO
{
    protected LLPWriter so;
    protected LLPReader si;
    protected Socket socket;

    protected static Category log;
    
    protected void initIO() {
        log = Category.getInstance(getClass());
        log.info("--- initIO() ---");
        try {
            if (so == null)
            {
                so = new LLPWriter(socket.getOutputStream());
            }
        }
        catch (Exception e) {
            log.error("Could not create LLPWriter : " + e.getMessage());
        }

        try
        {
            if (si == null)
                si = new LLPReader(socket.getInputStream());
        }
        catch (Exception e) {
            log.error("Could not create LLPReader : " + e.getMessage());
        }
    }

}
