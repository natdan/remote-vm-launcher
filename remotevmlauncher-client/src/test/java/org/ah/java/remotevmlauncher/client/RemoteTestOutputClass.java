package org.ah.java.remotevmlauncher.client;

public class RemoteTestOutputClass {

    public static void main(String[] args) throws Exception {
        System.out.println("Hello - this is first output");
        System.out.println("Got following arguments: ");
        for (int i = 0; i < args.length; i++) {
            System.out.println(i + " " + args[i]);
        }
        Thread.sleep(2000);
        System.out.println("Next line");
        Thread.sleep(2000);
        System.out.println("And ending now");
        Thread.sleep(1000);
    }
    
}
