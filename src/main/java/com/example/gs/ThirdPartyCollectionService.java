package com.example.gs;

import com.facebook.swift.codec.ThriftField;
import com.facebook.swift.service.ThriftMethod;
import com.facebook.swift.service.ThriftService;

import java.lang.reflect.InvocationTargetException;
import java.util.List;


@ThriftService("ThirdPartyCollectionService")
public interface ThirdPartyCollectionService {

    @ThriftMethod
    String save(@ThriftField(name = "collection") ThirdPartyCollection collection);

}
