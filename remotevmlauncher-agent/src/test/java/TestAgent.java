import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import org.ah.java.remotevmlauncher.agent.Agent;
import org.ah.java.remotevmlauncher.protocol.MainClassProcessor;
import org.ah.java.remotevmlauncher.protocol.ProtocolStateMachine;
import org.ah.java.remotevmlauncher.protocol.ReadyProcessor;
import org.ah.java.remotevmlauncher.protocol.StreamProcessor;


public class TestAgent {

    public static void main(String[] args) throws Exception {

        Thread agentThread = new Thread(new Runnable() {
            public void run() {
                try {
                    Agent.main(new String[0]);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        agentThread.start();

        Socket socket = null;
        while (socket == null) {
            try {
                socket = new Socket("localhost", 8999);
            } catch (Exception ignore) {
                Thread.sleep(100);
            }
        }

        InputStream inputStream = socket.getInputStream();
        BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
        DataInputStream dataInputStream = new DataInputStream(bufferedInputStream);
        OutputStream outputStream = socket.getOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(outputStream);

        ProtocolStateMachine stateMachine = new ProtocolStateMachine();

        ReadyProcessor readyProcessor = new ReadyProcessor();
        stateMachine.getProcessorMap().put(ReadyProcessor.ID, readyProcessor);

        StreamProcessor streamProcessor = new StreamProcessor() {
            public void invoke() {
                System.out.println(getReceived());
            }
        };
        stateMachine.getProcessorMap().put("S0", streamProcessor);

        System.out.println("Starting...");
        synchronized (readyProcessor) {
            while (!readyProcessor.isReady()) {
                stateMachine.processInput(dataInputStream);
            }
        }
        System.out.println("Started.");

        MainClassProcessor mainClassProcessor = new MainClassProcessor();
        mainClassProcessor.setMainClass("org.package.something.NewMainClassToInvoke");
        mainClassProcessor.send(dataOutputStream);

        System.out.println("----------------------------------------------------------------------------------------------");

        try {
            while (true) {
                stateMachine.processInput(dataInputStream);
            }
        } catch (EOFException e) {
        }
        System.out.println("----------------------------------------------------------------------------------------------");
        System.out.println("End.");
    }
}
