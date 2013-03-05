/*
 * JEngine, the OpenSource Integration Engine
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 *
 * $Id: Main.java,v 1.1.2.1 2002/07/18 19:52:51 alanshields Exp $
 * $Revision: 1.1.2.1 $
 */

package org.jengine;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.net.URL;
import java.security.*;
import java.util.*;

import javax.management.ObjectName;
import javax.management.MalformedObjectNameException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.RuntimeMBeanException;
import javax.management.MBeanException;
import javax.management.RuntimeErrorException;
import javax.management.RuntimeOperationsException;
import javax.management.ReflectionException;

import javax.management.loading.*;

/** The JEngine server main entry point.

 *   @author Rickard Öberg (rickard.oberg@telkel.com)
 *   @author <a href="mailto:docodan@nycap.rr.com">Daniel O'Connor</a>.
 *   @author Scott.Stark@jboss.org
 *   @version
 */
public class Main
{
   // Constants -----------------------------------------------------

   String versionIdentifier = "1.0";
   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------
   public static void main(final String[] args)
      throws Exception
   {
      Thread.currentThread().setName("JBossMain");
      String cn = "default"; // Default configuration name is "default", i.e. all conf files are in "/conf/default"
      String patchDir = null;
      // Given conf name?
      for(int a = 0; a < args.length; a ++)
      {
          if( args[a].startsWith("-p") )
          {
              a ++;
              patchDir = args[a];
              File tst = new File(patchDir);
              if( tst.exists() == false )
                 throw new IllegalArgumentException("Patch dir: "+tst.getAbsolutePath()+" does not exist");
          }
          else if( args[a].charAt(0) == '-' )
          {
             throw new IllegalArgumentException("Usage: [-p <patch_directory>] [<config_name>]");
          }
          else
          {
             cn = args[a];
          }
      }
      final String confName = cn;
      final String patchDirName = patchDir;

      // Load system properties
      URL jbossProps = Main.class.getClassLoader().getResource(confName+"/jboss.properties");
      InputStream propertiesIn = Main.class.getClassLoader().getResourceAsStream(confName+"/jboss.properties");
      if ( propertiesIn == null )
      {
         throw new IOException("jboss.properties missing");
      }

      System.getProperties().load(propertiesIn);

      /* Set a jboss.home property from the location of the Main.class jar
         if the property does not exist.
      */
      if( System.getProperty("jengine.home") == null )
      {
          String path = Main.class.getProtectionDomain().getCodeSource().getLocation().getFile();
          File runJar = new File(path);
          // Home dir should be the parent of the dir containing run.jar
          File homeDir = new File(runJar.getParent(), "..");
          System.setProperty("jengine.home", homeDir.getCanonicalPath());
      }
      System.out.println("jengine.home = "+System.getProperty("jengine.home"));

      // Set the JAAS login config file if not already set
      if( System.getProperty("java.security.auth.login.config") == null )
      {
          URL loginConfig = Main.class.getClassLoader().getResource(confName+"/auth.conf");
          if( loginConfig != null )
          {
              System.setProperty("java.security.auth.login.config", loginConfig.toExternalForm());
              System.out.println("Using JAAS LoginConfig: "+loginConfig.toExternalForm());
          }
          else
          {
              System.out.println("Warning: no auth.conf found in config="+confName);
          }
      }

      // Set security
      URL serverPolicy = Main.class.getClassLoader().getResource(confName+"/server.policy");

      if ( serverPolicy == null )
      {
         throw new IOException("server.policy missing");
      }

      System.setProperty("java.security.policy", serverPolicy.getFile());

      // Set security manager
      // Optional for better performance
      if (System.getProperty("java.security.manager") != null)
         System.setSecurityManager((SecurityManager)Class.forName(System.getProperty("java.security.manager")).newInstance());

      // Start server - Main does not have the proper permissions
      AccessController.doPrivileged(new PrivilegedAction()
      {
         public Object run()
         {
            new Main(confName, patchDirName);
            return null;
         }
      });
   }

   // Constructors --------------------------------------------------
   public Main(String confName, String patchDir)
   {
      long startTime = System.currentTimeMillis();

      try
      {
         System.out.println("Using configuration \""+confName+"\"");

            final PrintStream err = System.err;

         //com.sun.management.jmx.Trace.parseTraceProperties();

    // Give feedback about from where jndi.properties is read
    URL jndiLocation = this.getClass().getResource("/jndi.properties");
    if (jndiLocation instanceof URL) {
        System.out.println("Please make sure the following is intended (check your CLASSPATH):");
        System.out.println(" jndi.properties is read from "+jndiLocation);
    }

         // Create MBeanServer
         final MBeanServer server = MBeanServerFactory.createMBeanServer();

         // Add configuration directory to MLet
         URL confDirectory = new File("../conf/"+confName).getCanonicalFile().toURL();
         URL[] urls = {confDirectory};
         // Add any patch jars to the MLet so they are seen ahead of the JBoss jars
         if( patchDir != null )
         {
             File dir = new File(patchDir);
             ArrayList tmp = new ArrayList();
             File[] jars = dir.listFiles(new java.io.FileFilter()
                 {
                     public boolean accept(File pathname)
                     {
                         String name = pathname.getName();
                         return name.endsWith(".jar") || name.endsWith(".zip");
                     }
                 }
             );
             tmp.add(confDirectory);
             for(int j = 0; jars != null && j < jars.length; j ++)
             {
                 File jar = jars[j];
                 URL u = jar.getCanonicalFile().toURL();
                 tmp.add(u);
             }
             urls = new URL[tmp.size()];
             tmp.toArray(urls);
         }

         // Create MLet
         MLet mlet = new MLet(urls);
         server.registerMBean(mlet, new ObjectName(server.getDefaultDomain(), "service", "MLet"));

         // Set MLet as classloader for this app
         Thread.currentThread().setContextClassLoader(mlet);

         // Load configuration
         URL mletConf = mlet.getResource("jboss.conf");
         Set beans = (Set)mlet.getMBeansFromURL(mletConf);
         Iterator enum = beans.iterator();
         while (enum.hasNext())
         {
            Object obj = enum.next();
            if (obj instanceof RuntimeOperationsException)
               ((RuntimeOperationsException)obj).getTargetException().printStackTrace(err);
            else if (obj instanceof RuntimeErrorException)
               ((RuntimeErrorException)obj).getTargetError().printStackTrace(err);
            else if (obj instanceof MBeanException)
               ((MBeanException)obj).getTargetException().printStackTrace(err);
            else if (obj instanceof RuntimeMBeanException)
               ((RuntimeMBeanException)obj).getTargetException().printStackTrace(err);
            else if (obj instanceof ReflectionException)
               ((ReflectionException)obj).getTargetException().printStackTrace(err);
            else if (obj instanceof Throwable)
               ((Throwable)obj).printStackTrace(err);
         }

         // Load configuration
         server.invoke(new ObjectName(":service=Configuration"), "loadConfiguration", new Object[0], new String[0]);

         // Store configuration
         // This way, the config will always contain a complete mirror of what's in the server
         server.invoke(new ObjectName(":service=Configuration"), "saveConfiguration", new Object[0] , new String[0]);

         // Init and Start MBeans
         server.invoke(new ObjectName(":service=ServiceControl"), "init", new Object[0] , new String[0]);
         server.invoke(new ObjectName(":service=ServiceControl"), "start", new Object[0] , new String[0]);

      }
      catch (RuntimeOperationsException e)
      {
         System.out.println("Runtime error");
         e.getTargetException().printStackTrace();
      }
      catch (RuntimeErrorException e)
      {
         System.out.println("Runtime error");
         e.getTargetError().printStackTrace();
      }
      catch (MBeanException e)
      {
         e.getTargetException().printStackTrace();
      }
      catch (RuntimeMBeanException e)
      {
         e.getTargetException().printStackTrace();
      }
      catch (Exception e)
      {
         e.printStackTrace();
      }

      // Done
      long stopTime = System.currentTimeMillis();
      long lapsedTime = stopTime - startTime;
      long minutes = lapsedTime / 60000;
      long seconds = (lapsedTime - 60000 * minutes) / 1000;
      System.out.println("JEngine "+versionIdentifier+" Started in "+minutes+"m:"+seconds+"s");
   }
}

