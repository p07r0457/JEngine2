package org.jengine.tools.network;

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
 * $Id: TCPSend.java,v 1.4.2.1 2002/07/18 19:57:20 alanshields Exp $
 * $Revision: 1.4.2.1 $
 */

import java.net.Socket;
import java.io.*;

import org.jengine.tools.hl7.*;

public class TCPSend {

    private static final char LINEFEED = (char)0xa;

    public static void main(String[] args) {

        try
        {
            if (args[0]==null | args[1]==null | args[2]==null)
                System.out.println("USAGE: TCPSend ipAddress portNumber fileName");
            
            String ipAddr = args[0];
            int portNum = Integer.parseInt(args[1]);
            String fileName = args[2];
            
            System.out.println("\tIp Addr = " + ipAddr);
            System.out.println("\tPort  # = " + portNum);
            System.out.println("\tFile    = " + fileName);
            
            Socket senderSocket = new Socket(ipAddr, portNum);
            LLPWriter so = new LLPWriter(senderSocket.getOutputStream());
            LLPReader si = new LLPReader(senderSocket.getInputStream());
            
            File f = new File(fileName);
            FileInputStream fi = new FileInputStream(f);
            
            byte b[] = new byte[(int) f.length()];
            
            fi.read(b);
            String s = new String(b);
            
            System.out.println("XMIT");
            System.out.println(s);
            so.writeMsg(s);
            
            System.out.println("RECV");
            String msg = si.readMsg();
            System.out.println(msg);
        
            HL7Utils hl7Util = new HL7Utils(msg);
            if (hl7Util.isAck() == true)
                System.out.println("ACK GOTTEN");
            else
                System.out.println("ACK --not-- GOTTEN");
            
        }
        catch (Exception e) {
            System.out.println("TCPSend Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
