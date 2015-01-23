package org.ah.java.remotevmlauncher.client;

public class RemoteTestSystemExitClass {

    public static void main(String[] args) throws Exception {
        System.out.println("Hello - this is first output");
        System.out.println("Got following arguments: ");
        for (int i = 0; i < args.length; i++) {
            System.out.println(i + " " + args[i]);
        }
        System.out.println("Going to loop forever");
        while (true) {
            Thread.sleep(100);
        }
    }
    
}
