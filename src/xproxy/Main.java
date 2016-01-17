package xproxy;

import xproxy.httpp.HttpProxy;
import xproxy.httpp.StoreFilter;
import xproxy.io.NetModel;
import xproxy.socks.SocksProxy;
import xproxy.tcppm.PortMapper;
import xproxy.util.LogConfigure;
import xproxy.wsp.WebSocketProxy;

import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class Main {

    private static SocketAddress sa(String address) {
        String[] av = address.split(":");
        return (av.length == 1) ? new InetSocketAddress(Integer.parseInt(address)) :
                new InetSocketAddress(av[0], Integer.parseInt(av[1]));
    }

    public static void main(String[] args) throws IOException {
        Properties props = new Properties();
        for (String fn : args)
            props.load(new FileReader(fn));

        props.list(System.out);

        Set<String> keys = props.stringPropertyNames();

        if (keys.remove("log")) {
            String[] av = props.getProperty("log").split("\\s+");
            if (av.length == 2)
                LogConfigure.initialize(av[0], av[1]);
            else if (av.length == 4)
                LogConfigure.initialize(av[0], av[1], Integer.parseInt(av[2]), Integer.parseInt(av[3]));
        }

        if (keys.remove("cpu"))
            NetModel.initialize(Integer.parseInt(props.getProperty("cpu")));

        for (String key : keys) {
            String[] av = props.getProperty(key).split("\\s+");

            if (key.startsWith("tcppm")) {
                new PortMapper(sa(av[0]), sa(av[1]));

            } else if (key.startsWith("socks")) {
                new SocksProxy(sa(av[0]));

            } else if (key.startsWith("httpp")) {
                HttpProxy hp = new HttpProxy(sa(av[0]));
                if (av.length > 1)
                    hp.addFilter(new StoreFilter(av[1]));

            } else if (key.startsWith("wsp")) {
                Map<String, SocketAddress> res2socket = new HashMap<>();
                int cnt = Integer.parseInt(props.getProperty(av[2] + "_cnt"));
                for (int i = 0; i < cnt; i++) {
                    String[] pv = props.getProperty(av[2] + "_" + i).split("\\s+");
                    res2socket.put(pv[0], sa(pv[1]));
                }
                new WebSocketProxy(sa(av[0]), av[1], res2socket);

            } else if (!key.startsWith("_")) {
                System.out.println(key + " not implemented");
            }
        }
    }
}
