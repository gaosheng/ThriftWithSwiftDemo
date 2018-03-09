package com.example.gs.swifttest;

import com.example.gs.ThirdPartyCollection;
import com.example.gs.ThirdPartyCollectionService;
import com.facebook.nifty.client.FramedClientConnector;
import com.facebook.swift.service.ThriftClientManager;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;


public class ClientDemo {

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        ThriftClientManager clientManager = new ThriftClientManager();
        FramedClientConnector connector = new FramedClientConnector(new InetSocketAddress("localhost",8899));
        ThirdPartyCollectionService service = clientManager.createClient(connector, ThirdPartyCollectionService.class).get();
        String result = service.save(new ThirdPartyCollection(1001, "2014-08-29"));
        System.out.println("Response:" + result);
    }
}
