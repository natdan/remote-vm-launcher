package org.ah.java.remotevmlauncher.client;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;

import org.ah.java.remotevmlauncher.JavaLoggingUtils;
import org.ah.java.remotevmlauncher.agent.Agent;
import org.ah.java.remotevmlauncher.protocol.StreamProcessor;
import org.junit.Assert;
import org.junit.Test;

public class TestLaunchRemote {

    @Test
    public void testLaunchRemote() throws Exception {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.indexOf("windows") < 0) {
            
    
            int debugLevel = 0;
    
            JavaLoggingUtils.setupSimpleConsoleLogging(debugLevel);
    
            File here = new File(".");
    
            System.out.println("Here: " + here.getAbsolutePath());
    
            final String portStr = "localhost:8991";
    
            Thread agentThread = new Thread(new Runnable() {
                public void run() {
                    try {
                        Agent.main(Arrays.asList("-l", portStr).toArray(new String[0]));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            agentThread.start();
    
            Thread.sleep(2000);
    
            StringWriter result = new StringWriter();
            final PrintWriter out = new PrintWriter(result);
    
            LaunchRemote.DEFAULT_STREAM_PROCESSOR = new StreamProcessor() {
                @Override public void invoke() {
                    String received = getReceived();
                    out.print(received);
                    System.out.print(received);
                }
            };
            LaunchRemote.main(Arrays.asList(
                    "-d", Integer.toString(debugLevel),
                    "-rcp", "target/test-classes",
                    portStr,
                    RemoteTestOutputClass.class.getName(),
                    "--",
                    "arg1", "arg2"
            ).toArray(new String[0]));
    
            Agent.stopCurrentAgent();
    
            Assert.assertEquals("Hello - this is first output\n" 
                                + "Got following arguments: \n"
                                + "0 arg1\n"
                                + "1 arg2\n"
                                + "Next line\n"
                                + "And ending now\n", result.toString());
        }
    }
}
