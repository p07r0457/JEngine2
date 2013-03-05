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

import org.jengine.tools.network.*;
import org.jengine.tools.hl7.HL7Utils;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.jms.*;
import javax.naming.*;

import org.jboss.web.*;
import org.apache.log4j.Category;

public class HL7Client implements Runnable, MessageListener {

    private static String CONNECTION_FACTORY = "ConnectionFactory";
    private static String PROVIDER_URL       = "localhost";

    private QueueConnectionFactory factory;
    private QueueConnection connection;
    private QueueSession session;
    private QueueReceiver qReceiver;
    private QueueSender qSender;
    private Queue queue;
    private Queue errorQueue;

    private int port;
    private int resendFailedCount;
    private int connectRetryInterval;
    private int resendRetryInterval;
    private String IPAddress;
    private String QueueName;
    private String ErrorQueueName;

    private LLPWriter so;
    private LLPReader si;
    private Socket socket;
    private boolean retrySocketSend;

    private TextMessage msg;
    private String msgText;

    private String ifName;
    
    private boolean connectionStatus;
    private int numberMessages;
    private int numberMessagesFailed;
    private String timeStampLastMsg;
    private int nakCount;

    private ThreadPool threadPool;
    
    private Category log;

    // If we have an incorrect socket:port (or a dead peer) we need the
    // connection loops to terminate when we are stopped. - ATS
    private boolean running;

    public HL7Client()
    {
        factory = null;
        connection = null;
        session = null;
        qReceiver = null;
        qSender = null;
        queue = null;
        errorQueue = null;
        retrySocketSend = true;
        port = 0;
        msgText = null;
        so = null;
        si = null;
        socket = null;
        resendFailedCount = 20;
        resendRetryInterval = 5000;
        connectRetryInterval = 30000;
        ifName = "";
        threadPool = new ThreadPool();
        connectionStatus = false;
        numberMessages = 0;
        numberMessagesFailed = 0;
        timeStampLastMsg = "";
        nakCount = 0;
    }

    public boolean getConnectionStatus()
    {
        return connectionStatus;
    }
    public int getNumberMessages()
    {
        return numberMessages;
    }
    public int getNumberMessagesFailed()
    {
        return numberMessagesFailed;
    }
    public String getTimeStampLastMsg()
    {
        return timeStampLastMsg;
    }
        
    public void setResendFailedCount(int t)
    {
        resendFailedCount = t;
    }
    public int getResendFailedCount()
    {
        return resendFailedCount;
    }
    public void setResendRetryInterval(int t)
    {
        resendRetryInterval = t;
    }
    public int getResendRetryInterval()
    {
        return resendRetryInterval;
    }

    public void setConnectRetryInterval(int t)
    {
        connectRetryInterval = t;
    }
    public int getConnectRetryInterval()
    {
        return connectRetryInterval;
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

    public void setIPAddress(String ip) {
        IPAddress = ip;
    }

    public String getIPAddress() {
        return IPAddress;
    }

    public void setQueue(String q) {
        QueueName = q;
    }

    public String getQueue() {
        return QueueName;
    }

    public void setQueueError(String q) {
        ErrorQueueName = q;
    }

    public String getQueueError() {
        return ErrorQueueName;
    }

    public void start() {

        log = Category.getInstance(getClass()+":"+ifName);
        log.info("--- start() ---");

        running=true;

        try {
            Context ctx = getInitialContext();
            factory = (QueueConnectionFactory) ctx.lookup(CONNECTION_FACTORY);
            connection = factory.createQueueConnection();
            session = connection.createQueueSession(false, Session.CLIENT_ACKNOWLEDGE);
            
            queue = (Queue) ctx.lookup("queue/"+QueueName);
            qReceiver = session.createReceiver(queue);
            qReceiver.setMessageListener(this);
            
            queue = (Queue) ctx.lookup("queue/"+ErrorQueueName);
            qSender = session.createSender(queue);
            
        } catch(NamingException ne) {
                log.error("NamingException : Queue Init : " + ne.getMessage());
        } catch(JMSException je) {
                log.error("JMSException : Queue Init : " + je.getMessage());
        }

        /**
         * SET UP NEW THREAD HERE TO take care of setting up and monitoring
         * connection   ----> run()
         */
        threadPool.run(this);
        // stop this thread so the start() method completes even if connections
        // to external are not established
        // ATS, I dont think this is necessarry, we dont have anything
        // to stop at this point
        //this.stop();
    }

    public void initNetwork()
    {
        log.info("initSocket()");
        initSocket();
        // if we have been sitting in initSocket without a connection
        // we will come through here when stopped/undeployed, not a problem,
        // just a touch messy in the log file  - ATS
        if(running) {
          log.info("initIO()");
          initIO();
        }
    }

    public void run() {
        log.info("--- run() ---");
        /*
	 * try to connect here from this separate thread
         * periodically, "ping" the external system by 
         * -use Java 1.4 socket features?
         */
        while (socket == null && running )
	{
            initNetwork();
            if (socket != null)
            {
                try {
                    connection.start();
                }
                catch(JMSException je) {
                    log.error("JMSException : Queue connection.start() : " + je.getMessage());
                }
            }
	}
    }

    public void stop() {
        log.info("--- stop() ---");
        try {
            running=false;
            socket.close();
            connection.close();
        }
        catch (Exception e) {
        }
    }

    private Context getInitialContext() throws NamingException {
        log.info("--- getInitialContext() ---");
        Properties env = new Properties();
        env.put("java.naming.factory.initial", "org.jnp.interfaces.NamingContextFactory");
        env.put("java.naming.provider.url", PROVIDER_URL);
        env.put("java.naming.provider.url.pkgs", "org.jnp.interfaces");
        return new InitialContext(env);
    }

    private void initSocket() {
        while (socket == null && running )
        {
            try {
                log.info("new Socket(" + getIPAddress() + "," +  getPort() + ")");
                socket = new Socket(getIPAddress(), getPort());
                log.info("Connected with server " + socket.getInetAddress() + ":" + socket.getPort());
                connectionStatus = true;
            }
            catch(Exception e) {
                log.error("Cannot connect to host " + getIPAddress()
			+ " on port " + getPort() + " - sleep " + connectRetryInterval);
                //sleep and try to connect again
                try { Thread.sleep(connectRetryInterval); }
                catch (InterruptedException ie) { log.error("Sleep Interrupted Exception : " + ie.getMessage()); }
            }
        }
    }

    public void onMessage(Message message) {

        log.info("--- onMessage() ---");

        if (socket == null)
        {
                initNetwork();
        }

        if (socket != null) {
            retrySocketSend = true;
            readFromQ(message);
            while (retrySocketSend == true) {
	    	if (sendMsgToSocket() == true)
                {
	            //get hl7 ack
	            if (getResponse() == true)
	            {
	            	qAcknowledge();
	                retrySocketSend = false;
                        numberMessages++;                       //increment msg counter
                        timeStampLastMsg = getCurrentTime();    //set timestamp here
                        nakCount = 0;
                        break;
	            }
                    else
                    {
                        if (nakCount > resendFailedCount)
                        {
                            log.info("nakCount : " + nakCount + ", resendFailedCount : " + resendFailedCount);
                            sendMsgToQ(message.toString());
                            numberMessagesFailed++;
                            qAcknowledge();
                            log.info("EXPIRING MESSAGE : " + message.toString());
                            nakCount = 0;
                            break;  // break out of while loop trying to resend message
                        }
                    }
                }
                if (true)  //that is, if we've failed for any reason, reconnect
                {
                    try { Thread.sleep(resendRetryInterval); }
                    catch (InterruptedException ie) { log.error("Sleep Interrupted Exception : " + ie.getMessage()); }
	            if (socket == null)
	                initNetwork();
                }
	    }
        }
    }

    private void qAcknowledge()
    {
        try {
            log.info("msg.acknowledge()");
            msg.acknowledge();
        }
        catch (javax.jms.JMSException je) {
            log.error("msg.acknowledge() Exception : " + je.getMessage());
        }
    }

    private boolean getResponse()
    {
        try {
            log.info("readMsg() : check for ACK");
            String rStr = si.readMsg();
            log.info("read : " + rStr);
            
            if (isAck(rStr) == true)
                return true;
            else
            {
                nakCount++;
                return false;
            }
        }
        catch (RuntimeException re)
        {
            log.error("Socket Read Runtime Exception : " + re.getMessage());
            re.printStackTrace();
            closeStreams();
            return false;
        }
        catch (Exception e) {
            log.error("Socket Read Exception : " + e.getMessage());
            closeStreams();
            return false;
        }
    }

    private boolean sendMsgToQ(String text)
    {
        try {
            TextMessage message = session.createTextMessage();
            message.setText(text);
            qSender.send(message);
        } catch(Exception e) {
            log.error("sendMessageToQ() Exception : " + e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }
    
    private boolean sendMsgToSocket()
    {
        try {
            log.info("socket writeMsg()");
            so.writeMsg(msgText);
            return true;
        }
        catch (RuntimeException re)
        {
            log.error("Socket Write Runtime Exception : " + re.getMessage());
            re.printStackTrace();
            closeStreams();
        }
        catch (Exception e) {
            log.error("Socket Write Exception : " + e.getMessage());
            closeStreams();
        }
        return false;

    }

    private void readFromQ(Message message)
    {
        msg = (TextMessage)message;
        try
        {
            msgText = msg.getText();
        }
        catch (Exception e)
        {
            log.error("Exception msg.getText() : " + e.getMessage());
            //
            // HOW TO HANDLE THIS JMSException
            //  - retry getText() ??
            //
        }
        log.info("Received incoming Q message, size : " + msgText.length());
        log.debug("Q message : " + msgText);
    }

    private void closeStreams()
    {
        so.close();
        si.close();
	so = null;
	si = null;
        try
        {
            socket.close();
	    socket = null;
            connectionStatus = false;
        }
        catch (IOException ie)
        {
            log.error("socket.close() Exception : " + ie.getMessage());
        }
    }

    private boolean isAck(String msg) {
        boolean retval = false;
        HL7Utils hl7Util;
        
        try
        {
            hl7Util = new HL7Utils(msg);
            if (hl7Util.isAck() == true)
                retval = true;
            else
                retval = false;
        }
        catch (Exception e)
        {
            log.error("Exception in isAck() : " + e.getMessage());
            retval = false;
        }
        return retval;
    }

    private void initIO()
    {
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
    
    private String getCurrentTime()
    {
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());
        String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(DATE_FORMAT);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(cal.getTime());
    }
}
