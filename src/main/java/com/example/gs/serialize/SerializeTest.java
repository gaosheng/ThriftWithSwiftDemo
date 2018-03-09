package com.example.gs.serialize;


import com.example.gs.ThirdPartyCollection;
import com.example.gs.ThirdPartyCollectionServiceImpl;
import com.facebook.swift.codec.ThriftCodecManager;
import com.facebook.swift.service.metadata.ThriftMethodMetadata;
import com.facebook.swift.service.metadata.ThriftServiceMetadata;
import com.google.common.collect.ImmutableMap;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.*;
import org.apache.thrift.transport.TIOStreamTransport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.collect.Maps.newHashMap;

public class SerializeTest {

    private static final AtomicInteger sequenceId = new AtomicInteger(1);

    private static Map<Method, ThriftMethodHandler> thriftMethodHandlerMap;
    private static Map<String, ThriftMethodProcessor> thriftMethodProcessorMap;


    public static void main(String[] args) throws Throwable {

        Class clazz = Class.forName("com.example.gs.ThirdPartyCollectionService");
        Object service = new ThirdPartyCollectionServiceImpl();
        Object arg = new ThirdPartyCollection(1001, "2014-08-29");

        //初始化
        thriftMethodHandlerMap = createThriftMethodHandlerMap(clazz);
        thriftMethodProcessorMap = createThriftMethodProcessorMap(clazz, service);

        //调用ThirdPartyCollectionService的save方法
        Method method = clazz.getMethod("save", ThirdPartyCollection.class);
        ThriftMethodHandler methodHandler = thriftMethodHandlerMap.get(method);

        byte[] data;
        data = clientSend(methodHandler, arg);
        data = serverProcess(data);
        Object result = clientReceive(data, methodHandler);
        System.out.println("Response:" + result);
    }

    /**
     * 创建client用到的ThriftMethodHandler
     * @return
     */
    public static Map<Method, ThriftMethodHandler> createThriftMethodHandlerMap(Class clazz) {
        ThriftCodecManager codecManager = new ThriftCodecManager();
        ThriftServiceMetadata thriftServiceMetadata = new ThriftServiceMetadata(clazz, codecManager.getCatalog());
        ImmutableMap.Builder<Method, ThriftMethodHandler> methods = ImmutableMap.builder();
        for (ThriftMethodMetadata methodMetadata : thriftServiceMetadata.getMethods().values()) {
            ThriftMethodHandler thriftMethodHandler = new ThriftMethodHandler(methodMetadata, codecManager);
            methods.put(methodMetadata.getMethod(), thriftMethodHandler);
        }
        Map<Method, ThriftMethodHandler> thriftMethodHandlerMap = methods.build();
        return thriftMethodHandlerMap;
    }

    /**
     * 创建server用到的ThriftMethodProcessor
     * @return
     */
    public static Map<String, ThriftMethodProcessor> createThriftMethodProcessorMap(Class clazz, Object service) {
        ThriftCodecManager codecManager = new ThriftCodecManager();
        ThriftServiceMetadata thriftServiceMetadata = new ThriftServiceMetadata(clazz, codecManager.getCatalog());
        Map<String, ThriftMethodProcessor> processorMap = newHashMap();
        for (ThriftMethodMetadata methodMetadata : thriftServiceMetadata.getMethods().values()) {
            String methodName = methodMetadata.getName();
            ThriftMethodProcessor methodProcessor = new ThriftMethodProcessor(service, thriftServiceMetadata.getName(), methodMetadata, codecManager);
            if (processorMap.containsKey(methodName)) {
                throw new IllegalArgumentException("Multiple @ThriftMethod-annotated methods named '" + methodName + "' found in the given services");
            }
            processorMap.put(methodName, methodProcessor);
        }
        Map<String, ThriftMethodProcessor> thriftMethodProcessorMap = ImmutableMap.copyOf(processorMap);
        return thriftMethodProcessorMap;
    }

    public static byte[] clientSend(ThriftMethodHandler methodHandler, Object... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
        TIOStreamTransport transport = new TIOStreamTransport(out);
        TProtocol outProtocol = new TBinaryProtocol.Factory().getProtocol(transport);
        try {
            methodHandler.send(outProtocol, sequenceId.getAndIncrement(), args);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out.toByteArray();
    }

    public static byte[] serverProcess(byte[] data) {
        TProtocol in = new TBinaryProtocol.Factory().getProtocol(new TIOStreamTransport(new ByteArrayInputStream(data)));
        TMessage message = null;
        try {
            message = in.readMessageBegin();
        } catch (TException e) {
            e.printStackTrace();
        }
        int sequenceId = message.seqid;
        String methodName = message.name;

        ThriftMethodProcessor methodProcessor = thriftMethodProcessorMap.get(methodName);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(1024);
        TIOStreamTransport transport = new TIOStreamTransport(byteArrayOutputStream);
        TProtocol out = new TBinaryProtocol.Factory().getProtocol(transport);

        try {
            methodProcessor.process(in, out, sequenceId);
        } catch (Throwable t) {
            t.printStackTrace();
        }

        return byteArrayOutputStream.toByteArray();
    }

    public static Object clientReceive(byte[] data, ThriftMethodHandler methodHandler) {
        TProtocol in = new TBinaryProtocol.Factory().getProtocol(new TIOStreamTransport(new ByteArrayInputStream(data)));
        Object result = null;
        try {
            result = methodHandler.receive(in, sequenceId.get() - 1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }
}
