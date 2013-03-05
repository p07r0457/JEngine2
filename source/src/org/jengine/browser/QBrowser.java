package org.jengine.browser;

import java.util.*;
import java.net.*;
import javax.jms.*;
import javax.naming.*;

public class QBrowser
{
	private static final String CONNECTION_FACTORY = "ConnectionFactory";
	private static final String PROVIDER_URL       = "localhost";

        private static Context ctx;
        private static QueueConnectionFactory connFact;
        private static QueueConnection qConn;
        private static QueueSession qSess;
        private static QueueBrowser qBrowser;

	private static String defaultQueue = "queue/HL7QueueInLog";

        public QBrowser()
        {
        }

        private static Context getInitialContext() throws NamingException
	{
	        Properties env = new Properties();
	        env.put("java.naming.factory.initial", "org.jnp.interfaces.NamingContextFactory");
	        env.put("java.naming.provider.url", PROVIDER_URL);
	        env.put("java.naming.provider.url.pkgs", "org.jnp.interfaces");
	        return new InitialContext(env);
	}

        public static void init(String q)
        {
                try
                {
                        System.out.println("Initialize Context & Queue things");
			//jndi context
			//ctx = new InitialContext();
			ctx = getInitialContext();
	                //jndi lookup q connection factory
			connFact = (QueueConnectionFactory)ctx.lookup(CONNECTION_FACTORY);
	                //create q connection
			qConn = connFact.createQueueConnection();
	                //create q session
			qSess = (QueueSession)qConn.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
	                //create q browser
	                qBrowser = (QueueBrowser)qSess.createBrowser((Queue)ctx.lookup(q));
        	}
                catch (NamingException ne)
                {
                        System.out.println("init() NamingException : " + ne.getMessage());
                }
                catch (JMSException je)
                {
                        System.out.println("init() JMSException : " + je.getMessage());
                }
        }

        public static void main(String argv[])
        {
            if (argv.length > 0)
                init("queue/" + argv[0]);
            else
                init(defaultQueue);
            run();
        }

        private static void run()
        {
	        try
	        {
                        java.util.Enumeration e = qBrowser.getEnumeration();
                        for (int i=0 ; e.hasMoreElements() ; i++)
                        {
	                        TextMessage tMsg = (TextMessage)e.nextElement();
                                System.out.println("Q MSG : " + i);
                                System.out.println(tMsg.getText());
                                System.out.println("END Q MSG");
                        }
                        qBrowser.close();

	        }
	        catch(JMSException je)
	        {
                        System.out.println("main() JMSException : " + je.getMessage());
	        }
        }

}
