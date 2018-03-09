package com.example.gs.serialize;

import com.facebook.swift.codec.ThriftCodec;
import com.facebook.swift.codec.ThriftCodecManager;
import com.facebook.swift.codec.internal.TProtocolReader;
import com.facebook.swift.codec.internal.TProtocolWriter;
import com.facebook.swift.codec.metadata.ThriftFieldMetadata;
import com.facebook.swift.codec.metadata.ThriftType;
import com.facebook.swift.service.metadata.ThriftMethodMetadata;
import com.google.common.base.Defaults;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Primitives;
import com.google.common.reflect.TypeToken;
import org.apache.thrift.TApplicationException;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TMessageType;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolException;
import org.weakref.jmx.Managed;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Map;



public class ThriftMethodProcessor {

    private final String name;
    private final String serviceName;
    private final String qualifiedName;
    private final Object service;
    private final Method method;
    private final String resultStructName;
    private final boolean oneway;
    private final ImmutableList<ThriftFieldMetadata> parameters;
    private final Map<Short, ThriftCodec<?>> parameterCodecs;
    private final Map<Short, Short> thriftParameterIdToJavaArgumentListPositionMap;
    private final ThriftCodec<Object> successCodec;
    private final Map<Class<?>, ExceptionProcessor> exceptionCodecs;

    public ThriftMethodProcessor(
            Object service,
            String serviceName,
            ThriftMethodMetadata methodMetadata,
            ThriftCodecManager codecManager
    ) {
        this.service = service;
        this.serviceName = serviceName;

        name = methodMetadata.getName();
        qualifiedName = serviceName + "." + name;
        resultStructName = name + "_result";

        method = methodMetadata.getMethod();
        oneway = methodMetadata.getOneway();

        parameters = ImmutableList.copyOf(methodMetadata.getParameters());

        ImmutableMap.Builder<Short, ThriftCodec<?>> builder = ImmutableMap.builder();
        for (ThriftFieldMetadata fieldMetadata : methodMetadata.getParameters()) {
            builder.put(fieldMetadata.getId(), codecManager.getCodec(fieldMetadata.getThriftType()));
        }
        parameterCodecs = builder.build();

        // Build a mapping from thrift parameter ID to a position in the formal argument list
        ImmutableMap.Builder<Short, Short> parameterOrderingBuilder = ImmutableMap.builder();
        short javaArgumentPosition = 0;
        for (ThriftFieldMetadata fieldMetadata : methodMetadata.getParameters()) {
            parameterOrderingBuilder.put(fieldMetadata.getId(), javaArgumentPosition++);
        }
        thriftParameterIdToJavaArgumentListPositionMap = parameterOrderingBuilder.build();

        ImmutableMap.Builder<Class<?>, ExceptionProcessor> exceptions = ImmutableMap.builder();
        for (Map.Entry<Short, ThriftType> entry : methodMetadata.getExceptions().entrySet()) {
            Class<?> type = TypeToken.of(entry.getValue().getJavaType()).getRawType();
            ExceptionProcessor processor = new ExceptionProcessor(entry.getKey(), codecManager.getCodec(entry.getValue()));
            exceptions.put(type, processor);
        }
        exceptionCodecs = exceptions.build();

        successCodec = (ThriftCodec<Object>) codecManager.getCodec(methodMetadata.getReturnType());
    }

    @Managed
    public String getName() {
        return name;
    }

    public Class<?> getServiceClass() {
        return service.getClass();
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public boolean process(TProtocol in, final TProtocol out, final int sequenceId) throws Throwable {
        boolean success = true;
        // read args
        Object[] args = readArguments(in);
        in.readMessageEnd();

        Object result;

        // invoke method
        result = invokeMethod(args);

        try {
            writeResponse(out,
                    sequenceId,
                    TMessageType.REPLY,
                    "success",
                    (short) 0,
                    successCodec,
                    result);
        } catch (Throwable t) {
            success = false;
            ExceptionProcessor exceptionCodec = exceptionCodecs.get(t.getClass());
            if (exceptionCodec != null) {
                // business exception, write expected exception response
                writeResponse(
                        out,
                        sequenceId,
                        TMessageType.REPLY,
                        "exception",
                        exceptionCodec.getId(),
                        exceptionCodec.getCodec(),
                        t);
            } else {
                // Other exceptions, include TException, RTE, etc...
                throw t;
            }
        }
        return success;
    }


    private Object invokeMethod(Object[] args) {
        Object response = null;
        try {
            response = method.invoke(service, args);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return response;
    }

    private Object[] readArguments(TProtocol in)
            throws Exception {
        try {
            int numArgs = method.getParameterTypes().length;
            Object[] args = new Object[numArgs];
            TProtocolReader reader = new TProtocolReader(in);

            // Map incoming arguments from the ID passed in on the wire to the position in the
            // java argument list we expect to see a parameter with that ID.
            reader.readStructBegin();
            while (reader.nextField()) {
                short fieldId = reader.getFieldId();

                ThriftCodec<?> codec = parameterCodecs.get(fieldId);
                if (codec == null) {
                    // unknown field
                    reader.skipFieldData();
                } else {
                    // Map the incoming arguments to an array of arguments ordered as the java
                    // code for the handler method expects to see them
                    args[thriftParameterIdToJavaArgumentListPositionMap.get(fieldId)] = reader.readField(codec);
                }
            }
            reader.readStructEnd();

            // Walk through our list of expected parameters and if no incoming parameters were
            // mapped to a particular expected parameter, fill the expected parameter slow with
            // the default for the parameter type.
            int argumentPosition = 0;
            for (ThriftFieldMetadata argument : parameters) {
                if (args[argumentPosition] == null) {
                    Type argumentType = argument.getThriftType().getJavaType();

                    if (argumentType instanceof Class) {
                        Class<?> argumentClass = (Class<?>) argumentType;
                        argumentClass = Primitives.unwrap(argumentClass);
                        args[argumentPosition] = Defaults.defaultValue(argumentClass);
                    }
                }
                argumentPosition++;
            }

            return args;
        } catch (TProtocolException e) {
            // TProtocolException is the only recoverable exception
            // Other exceptions may have left the input stream in corrupted state so we must
            // tear down the socket.
            throw new TApplicationException(TApplicationException.PROTOCOL_ERROR, e.getMessage());
        }
    }

    private <T> void writeResponse(TProtocol out,
                                   int sequenceId,
                                   byte responseType,
                                   String responseFieldName,
                                   short responseFieldId,
                                   ThriftCodec<T> responseCodec,
                                   T result) throws Exception {
        out.writeMessageBegin(new TMessage(name, responseType, sequenceId));

        TProtocolWriter writer = new TProtocolWriter(out);
        writer.writeStructBegin(resultStructName);
        writer.writeField(responseFieldName, (short) responseFieldId, responseCodec, result);
        writer.writeStructEnd();

        out.writeMessageEnd();
        out.getTransport().flush();
    }

private static final class ExceptionProcessor {
    private final short id;
    private final ThriftCodec<Object> codec;

    private ExceptionProcessor(short id, ThriftCodec<?> coded) {
        this.id = id;
        this.codec = (ThriftCodec<Object>) coded;
    }

    public short getId() {
        return id;
    }

    public ThriftCodec<Object> getCodec() {
        return codec;
    }
}

}
