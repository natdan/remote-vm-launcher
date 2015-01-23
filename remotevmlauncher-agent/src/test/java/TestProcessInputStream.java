import java.io.FilterInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;


public class TestProcessInputStream {

    public static void main(String[] args) throws Exception {
        
        System.out.println("Start:");
        System.out.println("----------------------------------------------------------------------------------------------");
        ProcessBuilder processBuilder = new ProcessBuilder("java", "-cp", ".", "TestSomeOutput");
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        
        InputStream inputStream = process.getInputStream();
        if (inputStream instanceof FilterInputStream) {
            Field f = FilterInputStream.class.getDeclaredField("in");
            f.setAccessible(true);

            inputStream = (InputStream)f.get(inputStream);
        }
        
        @SuppressWarnings("unused")
        ReadableByteChannel channel = Channels.newChannel(inputStream);
        
        byte[] buf = new byte[1000];
        int r = inputStream.read(buf);
        while (r > 0) {
            System.out.print(new String(buf, 0, r));
            r = inputStream.read(buf);
        }
        System.out.println("----------------------------------------------------------------------------------------------");
        System.out.println("End.");
    }
}
