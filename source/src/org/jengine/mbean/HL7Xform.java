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

public class HL7Xform implements Runnable, MessageListener {

    private static String CONNECTION_FACTORY = "ConnectionFactory";
    private static String PROVIDER_URL       = "localhost";

    private QueueConnectionFactory factory;
    private QueueConnection connection;
    private QueueSession session;
    private QueueReceiver receiver;
    private QueueSender sender;

    private String inQueueName;
    private String outQueueName;
    private Queue inQueue;
    private Queue outQueue;
   
    private Transformation transformation;
    private String transformationProperties;

    private TextMessage msg;
    private String msgText;

    private String ifName;

    private Category log;

    public HL7Xform()
    {
        factory = null;
        connection = null;
        session = null;
        receiver = null;
        sender = null;
        inQueue = null;
        outQueue = null;
        msgText = null;
        ifName = "";
    }

    public void setTransformationProperties(String properties) {
      if(this.transformation!=null) this.transformation.init(ifName,properties);
    }

    public void setTransformation(Transformation transformation) {
      this.transformation=transformation;
      if(transformationProperties!=null) transformation.init(ifName,transformationProperties);
    }

    public void setIFName(String n)
    {
        ifName = n;
    }
    public String getIFName()
    {
        return ifName;
    }

    public void setInQueue(String q) {
        inQueueName = q;
    }

    public String getInQueue() {
        return inQueueName;
    }

    public void setOutQueue(String q) {
        outQueueName = q;
    }

    public String getOutQueue() {
        return outQueueName;
    }

    public void start() {
        log = Category.getInstance(getClass());

        log.info("--- start() ---");

        try {
            Context ctx = getInitialContext();
            factory = (QueueConnectionFactory) ctx.lookup(CONNECTION_FACTORY);
            connection = factory.createQueueConnection();

            //make this a transacted session
            //only commit the send and recv if both work w/o exception
            //and everything in between (xformation) works.
            session = connection.createQueueSession(true, Session.CLIENT_ACKNOWLEDGE);

            inQueue = (Queue)ctx.lookup("queue/" + inQueueName);
            receiver = session.createReceiver(inQueue);
            receiver.setMessageListener(this);

            outQueue = (Queue) ctx.lookup("queue/" + outQueueName);
            sender = session.createSender(outQueue);

        } catch(NamingException ne) {
                log.error("NamingException : Queue Init : " + ne.getMessage());
        } catch(JMSException je) {
                log.error("JMSException : Queue Init : " + je.getMessage());
        }

        //if any messages exist in the Q, they will trigger onMessage()
        //following the start() here
        try {
	    connection.start();
        } catch(JMSException je) {
            log.error("JMSException : Queue connection.start() : " + je.getMessage());
        }

    }

    public void run() {
        log.info("--- run() ---");
    }

    public void stop() {
        log.info("--- stop() ---");
        try {
            connection.close();
        }
        catch (Exception e) {
	        log.error("connection.close() Exception in stop() : " + e.getMessage());
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

    public void onMessage(Message message) {

        log.info("--- onMessage() ---");

	try
	{
		TextMessage inMsg = (TextMessage)message;
		System.out.println("[" + ifName + "] " + "message read from Q");

                /*
                 * HERE IS WHERE THE XLATION WOULD TAKE PLACE
                 */
                String trx=inMsg.getText();
                if (transformation != null ) trx=transformation.transform(trx);
                else log.warn("No transformation performed");

		TextMessage outMsg = session.createTextMessage();
		outMsg.setText(trx);

                try
		{
			sender.send(outMsg);
			System.out.println("[" + ifName + "] " + "message sent to outQ, ack on inQ");
	                if (qAcknowledge(inMsg) == true)
                        {
        	        	session.commit();
				System.out.println("[" + ifName + "] " + "Transaction commit'd");
                        }
                        else
                        {
                                //what happens if rollback fails??
                                //will next commit that works commit
				//the previously unwanted sent message (outQ) ???
                                session.rollback();
				System.out.println("[" + ifName + "] " + "Transaction rollback'd");
                        }
		}
                catch (JMSException je)
		{
			System.out.println("[" + ifName + "] " + "JMSException on Q send/commit : " + je.getMessage());
		}
	}
	catch (JMSException je)
	{
		System.out.println("[" + ifName + "] " + "JMSException : " + je.getMessage());
		je.printStackTrace();
	}
	catch (Exception e)
	{
		System.out.println("[" + ifName + "] " + "Exception : " + e.getMessage());
		e.printStackTrace();
	}
    }

    private boolean qAcknowledge(TextMessage msg)
    {
        try {
            log.info("Sending msg.acknowledge()");
            msg.acknowledge();
            return true;
        }
        catch (javax.jms.JMSException je) {
            log.error("msg.acknowledge() Exception : " + je.getMessage());
            return false;
        }
    }

}
