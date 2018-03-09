package com.example.gs;

import com.facebook.swift.codec.ThriftConstructor;
import com.facebook.swift.codec.ThriftField;
import com.facebook.swift.codec.ThriftStruct;

@ThriftStruct
public final class ThirdPartyCollection {
    public final long id; // required
    public final String date; // optional

    @ThriftConstructor
    public ThirdPartyCollection(long id, String date) {
        this.id = id;
        this.date = date;
    }
    @ThriftField(1)
    public long getId() {
        return id;
    }
    @ThriftField(2)
    public String getDate() {
        return date;
    }

    @Override
    public String toString() {
        return "ThirdPartyCollection{" +
                "id=" + id +
                ", date='" + date + '\'' +
                '}';
    }
}
