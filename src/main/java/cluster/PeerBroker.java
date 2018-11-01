/**
 * @author anpham
 * Local cluster or a leaf cluster
 */

package cluster;

import common.Msg;
import common.Shared;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

import java.util.Arrays;
import java.util.Random;

public class PeerBroker {
    public static ZContext ctx;
    public static ZMQ.Socket pubSock;
    public static ZMQ.Socket routerSock;
    private static ZMQ.Socket subscriber;
    private static ZMQ.Socket dealer;
    private String name;

    public PeerBroker(String name) {
        this.name = name;
    }

    public static void main(String[] args) {
        PeerBroker self = new PeerBroker("Cl1");

        self.initializeGateway();

        self.initializeNodes(args);

        while (true) {
            //  Poll for activity, or 1 second timeout
            ZMQ.Poller poller = self.ctx.createPoller(3);
            poller.register(self.routerSock, ZMQ.Poller.POLLIN);
            poller.register(self.subscriber, ZMQ.Poller.POLLIN);
            poller.register(self.dealer, ZMQ.Poller.POLLIN);

            int rc = poller.poll(10 * 1000);
            if (rc == -1)
                break; //  Interrupted

            ZMsg result;

            if (poller.pollin(0)) {
                //  Handle incoming status messages from Router socket
                result = ZMsg.recvMsg(self.routerSock);
                try {
                    result.send(self.dealer, false);
                    System.out.println("BROKER: Receive request from cloud dealer: " + result);

                    if (result.peekLast().toString().charAt(0) != 'R') {
                        System.out.println("BROKER: Send to pubSock");
                        result.send(self.pubSock);
                    } else {
                        System.out.println("BROKER: Forward this to routerSock");
                        result.send(self.routerSock);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (poller.pollin(1)) {
                // for published msg from central node
                result = ZMsg.recvMsg(self.subscriber);
                result.dump();

                // AnP: Redirect this msg to all nodes.
                result.send(self.pubSock);

            } else if (poller.pollin(2)) {
                // for direct requests from cloud
                result = ZMsg.recvMsg(self.dealer);
                System.out.println("Receive response msg" + result);

                try {
                    Msg m = new Msg(result);
                    m.dump();

                    byte[] msgContent = m.Command;
                    int msgLength = msgContent.length;
//                    byte[] peerDst = Arrays.copyOfRange(msgContent, msgLength - 2, msgLength - 1);
//                    byte[] response = Arrays.copyOfRange(msgContent, 0, msgContent.length - 3);

                    Shared.getResponseMessage(msgContent, m.Destination.getBytes()).send(self.routerSock);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        self.ctx.destroy();
    }

    /**
     * @param args numberOfNodes - nChunks - chunkSize - hammingThres - threshold C - numberOfGui
     */
    private void initializeNodes(String[] args) {
        int numberOfNodes = Integer.parseInt(args[0]);
        int nChunks = Integer.parseInt(args[1]);
        int chunkSize = Integer.parseInt(args[2]);
        int hammingThres = Integer.parseInt(args[3]);
        int cThres = Integer.parseInt(args[4]);
        int numberOfGui = Integer.parseInt(args[5]);

        int[] rand = new int[]{
                new Random().nextInt(numberOfNodes),
                new Random().nextInt(numberOfNodes),
                new Random().nextInt(numberOfNodes),
                new Random().nextInt(numberOfNodes)
        };

        for (int i = 0; i < numberOfNodes; i++) {
            boolean withGui = false;
            if (numberOfGui > 0) {
                // TODO: Improve randomness of node pick
                withGui = true;
                numberOfGui--;
            }
            new Thread(new PeerNode(this, nChunks, chunkSize, hammingThres, cThres, withGui, i)).start();
        }
    }

    public void initializeGateway() {
        ctx = new ZContext();

        // Socket to talk to peer node
        routerSock = ctx.createSocket(ZMQ.ROUTER);
        routerSock.bind(String.format(Shared.LOCAL_ROUTER_SOCK, name));

        //  Bind insert backend to endpoint
        pubSock = ctx.createSocket(ZMQ.PUB);
        pubSock.bind(String.format(Shared.LOCAL_PUBLISH_SOCK, name));

//        context=new ZContext(1);
        subscriber = ctx.createSocket(ZMQ.SUB);
        dealer = ctx.createSocket(ZMQ.DEALER);
        byte[] identity = new byte[2];
        new Random().nextBytes(identity);
        dealer.setIdentity(identity);

        //bindings....
        subscriber.connect(String.format(Shared.CENTRAL_ADDR, Shared.PUB_SUB_PROTOCOL, Shared.PUB_SUB_PORT));
        subscriber.subscribe("");
        dealer.connect(String.format(Shared.CENTRAL_ADDR, Shared.ROUTER_DEALER_PROTOCOL, Shared.ROUTER_DEALER_PORT));
//                Resources.getInstance().ROUTER_DEALER_PROTOCOL,Resources.getInstance().ROUTER_DEALER_PORT));
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            System.err.println("DEALER is waiting a bit to connect to ROUTER... Please don't interrupt!");
        }
        System.out.println("finished Initializing DEALER");
    }

    public String getName() {
        return name;
    }
}
