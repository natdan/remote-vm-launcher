//import java.net.Socket;


public class TestSomeOutput {

    public static void main(String[] args) throws Exception {
//        int port = Integer.parseInt(args[0]);
//        
//        Socket socket = new Socket("localhost", port);
//        socket.getOutputStream().write("OK".getBytes());
//        socket.getOutputStream().flush();
//        byte[] in = new byte[1024];
//        
//        int r = socket.getInputStream().read(in);
//        
//        System.out.println("ECHO: " + new String(in, 0, r));
        
        System.out.println("Hello - this is first output");
        Thread.sleep(2000);
        System.out.println("Next line");
        Thread.sleep(2000);
        System.out.println("And ending now");
        Thread.sleep(1000);
        System.exit(0);
    }
    
}
