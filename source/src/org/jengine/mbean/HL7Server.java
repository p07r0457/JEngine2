/*
 * 	JEngine Copyright (C) 2001
 *  	Jacek Zagorski, Shasta NetWorks LLC
 *  	Jeff Gunther, Intalgent Technologies, LLC
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
 */

package org.jengine.mbean;

import org.jengine.tools.hl7.*;
import org.jengine.tools.network.*;

import org.jboss.web.*;
import org.apache.log4j.Category;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.jms.*;
import javax.naming.*;

public class HL7Server implements Runnable {

    //private static String QUEUE_NAME         = "queue/HL7QueueIn";
    private static String CONNECTION_FACTORY = "ConnectionFactory";
    private static String PROVIDER_URL       = "localhost";

    private Context ctx = null;
    private QueueConnectionFactory factory = null;
    private QueueConnection connection = null;
    private QueueSession session = null;
    private QueueSender qSender = null;
    private Queue queue = null;

    private int port = 0;
    private InetAddress bindAddress;
    private int backlog = 50;
    private ServerSocket server = null;
    private ThreadPool threadPool = new ThreadPool();

    private boolean connectionStatus;
    private int numberMessages;
    private String timeStampLastMsg;

    private String QueueNames = null;
    private ArrayList Queues = new ArrayList();
    private ArrayList QSenders = new ArrayList();

    private String ifName = "";

    private Category log;

    public HL7Server()
    {
        connectionStatus = false;
        numberMessages = 0;
        timeStampLastMsg = "";
    }
    
    public boolean getConnectionStatus()
    {
        return connectionStatus;
    }
    public int getNumberMessages()
    {
        return numberMessages;
    }
    public String getTimeStampLastMsg()
    {
        return timeStampLastMsg;
    }
        

    public void setIFName(String n)
    {
        ifName = n;
    }
    public String getIFName()
    {
        return ifName;
    }

    public void setPort(int p) {
        port = p;
    }

    public int getPort() {
        return port;
    }

    public String getBindAddress() {
        String address = null;
        if( bindAddress != null )
            address = bindAddress.getHostAddress();
        return address;
    }

    public void setBindAddress(String host) {
        try {
            if (host != null)
                bindAddress = InetAddress.getByName(host);
        }
        catch(UnknownHostException e) {
            String msg = "Invalid host address specified: " + host;
            log.error(msg, e);
        }
    }

    public int getBacklog() {
        return backlog;
    }

    public void setBacklog(int backlog) {
        if( backlog <= 0 )
            backlog = 50;
        this.backlog = backlog;
    }

    public void setQueues(String q) {
        QueueNames = q;
    }

    public String getQueues() {
        return QueueNames;
    }

    public void start() throws IOException {
        log = Category.getInstance(getClass()+":"+ifName);
        try {
            ctx = getInitialContext();

            factory = (QueueConnectionFactory) ctx.lookup(CONNECTION_FACTORY);
            connection = factory.createQueueConnection();
            //session = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
            session = connection.createQueueSession(false, Session.CLIENT_ACKNOWLEDGE);

            StringTokenizer st = new StringTokenizer(QueueNames,":");
            while(st.hasMoreElements())
            {
                queue = (Queue)ctx.lookup("queue/" + st.nextElement());
                Queues.add(queue);
                qSender = session.createSender(queue);
                QSenders.add(qSender);
            }

            server = new ServerSocket(port, backlog, bindAddress);
            listen();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void stop() {
        try {
            ServerSocket srv = server;
            server = null;
            srv.close();
            session.close();
        }
        catch (Exception e) {
        }
    }

    public void run()
    {
        Socket socket;

        try {
                socket = server.accept();
                listen();
                connectionStatus = true;
                log.info("New connection accepted "
                    + socket.getInetAddress() + ":" + socket.getPort());
                LLPWriter so = new LLPWriter(socket.getOutputStream());
                LLPReader si = new LLPReader(socket.getInputStream());
                while(true) {
	        	log.info("Connected - readSocket()");
                        String message = readMsg(si);
                        if (message == null)
				break;
                        log.info(message);
                        log.info("Connected - sendMsgToQ()");
	        	if (sendMsgToQ(message) != true)
                        {   //NO ACK TO EXTERNAL IF ERROR
	                    log.info("sendMsgToQ() error");
                            //SHOULD RE-INIT QUEUES HERE
			    break;
                        }
	        	log.info("Connected - sendResponse()");
                        if (sendResponse(so, message) != true)
                        {
	                    log.info("sendResponse() error");
                            break;
                        }
                        numberMessages++;   //increment msg counter
                        timeStampLastMsg = getCurrentTime(); //set timestamp here
                 }

		// connection closed by client
                // OR
                // send2Q error
		// OR
		// sendResponse error
		try {
                    connectionStatus = false;
                    socket.close();
                    System.out.println("Connection closed by client");
                }
                catch (IOException e) {
                    System.out.println(e);
                }
        }
        catch (IOException e) {
            System.out.println(e);
        }
    }

    protected void listen() {
        threadPool.run(this);
    }

    private Context getInitialContext() throws NamingException {
        Properties env = new Properties();
        env.put("java.naming.factory.initial", "org.jnp.interfaces.NamingContextFactory");
        env.put("java.naming.provider.url", PROVIDER_URL);
        env.put("java.naming.provider.url.pkgs", "org.jnp.interfaces");
        return new InitialContext(env);
    }

    private boolean sendResponse(LLPWriter so, String msg) {
        try {
            // send ACK/NAK
            // HL7Ack(success|failure, mcId)
            HL7Ack ack = new HL7Ack(true, msg);
            log.debug("Send ACK : " + ack.getMessage());
            so.writeMsg(ack.getMessage());
            return true;
        }
        catch (Exception ex) {
            log.error("Write ACK Exception : " + ex.getMessage());
            ex.printStackTrace();
            return false;
        }
    }

    private String readMsg(LLPReader si) {
        try {
            return si.readMsg();
        }
        catch (Exception e) {
            log.debug("si.readMsg() Exception : " + e.getMessage());
            return null;
        }
    }

    private boolean sendMsgToQ(String text) {

        Iterator it = QSenders.iterator();

        while (it.hasNext()) {
            try {
                qSender = (QueueSender)it.next();
                TextMessage message = session.createTextMessage();
                message.setText(text);
                qSender.send(message);
            } catch(Exception e) {
                log.error("sendMsgToQ() Exception : " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }//end of while
        return true;
    }

    private String getCurrentTime()
    {
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());
        String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(DATE_FORMAT);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(cal.getTime());
    }

}