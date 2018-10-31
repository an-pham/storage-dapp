package central;


import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;
import org.zeromq.ZSocket;

public class testPeerBroker {
    private static ZContext context;
    private static ZMQ.Socket subscriber;
    private static ZMQ.Socket dealer;

    //Init stuff in constructor......
    public testPeerBroker(){
        context=new ZContext(1);
        subscriber=context.createSocket(ZMQ.SUB);
        dealer=context.createSocket(ZMQ.DEALER);

        //bindings....
        subscriber.connect(String.format("%s://localhost:%s",
                Resources.getInstance().PUB_SUB_PROTOCOL,Resources.getInstance().PUB_SUB_PORT));
        dealer.setIdentity("0913404k4822".getBytes());
        dealer.connect(String.format("%s://localhost:%s",
                Resources.getInstance().ROUTER_DEALER_PROTOCOL,Resources.getInstance().ROUTER_DEALER_PORT));
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            System.err.println("DEALER is waiting a bit to connect to ROUTER... Please don't interrupt!");
        }
        System.out.println("finished Initing DEALER");
    }

    public static void cleanup()
    {
        subscriber.close();
        dealer.close();
        context.destroy();
    }
    public static void main(String[] args)throws Exception{
        //initialize sockets
        new testPeerBroker();

        //handle interrupt to cleanup properly
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                System.out.println("How dare you interrupted me bastard?");
                cleanup();
            }
        }));


        ZMsg outgoing=ZMsg.newStringMsg("JOIN");
        outgoing.push("44");
        outgoing.send(dealer);

        ZMsg incoming;
        //mainloop
        while(true)
        {
            incoming=ZMsg.recvMsg(dealer);
            System.out.print("DEALER: incoming message of size: "+incoming.size());
            //loop to handle message according to its type;;;
        }
    }

}