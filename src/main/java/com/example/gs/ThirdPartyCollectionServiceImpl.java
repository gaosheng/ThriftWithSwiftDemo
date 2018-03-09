package com.example.gs;

import com.facebook.swift.codec.ThriftField;

import java.lang.reflect.InvocationTargetException;
import java.util.List;


public class ThirdPartyCollectionServiceImpl implements ThirdPartyCollectionService {

    public String save(@ThriftField(name = "collection") ThirdPartyCollection collection) {
        System.out.println("Request:" + collection.toString());
        return "Success";
    }

}
