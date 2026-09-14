// Copyright Aliaksei Levin (levlam@telegram.org), Arseny Smirnov (arseny30@gmail.com) 2014-2026
// Distributed under the Boost Software License, Version 1.0.
package org.drinkless.tdlib;

/** Main class for interaction with TDLib using the official JSON interface. */
public final class JsonClient {
    static {
        try {
            System.loadLibrary("tdjsonjava");
        } catch (UnsatisfiedLinkError e) {
            e.printStackTrace();
        }
    }

    public static native int createClientId();
    public static native void send(int clientId, String request);
    public static native String receive(double timeout);
    public static native String execute(String request);

    public interface LogMessageHandler {
        void onLogMessage(int verbosityLevel, String message);
    }

    public static native void setLogMessageHandler(int maxVerbosityLevel, LogMessageHandler logMessageHandler);

    private JsonClient() { }
}
