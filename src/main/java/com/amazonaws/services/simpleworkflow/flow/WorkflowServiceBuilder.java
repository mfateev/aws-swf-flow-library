package com.amazonaws.services.simpleworkflow.flow;

import com.uber.cadence.WorkflowService;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.tchannel.api.SubChannel;
import com.uber.tchannel.api.TChannel;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TTransport;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

public class WorkflowServiceBuilder {

    private final String host;
    private final int port;
    private final WorkflowServiceTChannel.ClientOptions options;

    public WorkflowServiceBuilder(String host, int port) {
        this(host, port, new WorkflowServiceTChannel.ClientOptions.Builder().build());
    }

    public WorkflowServiceBuilder(String host, int port, WorkflowServiceTChannel.ClientOptions options) {
        this.host = host;
        this.port = port;
        this.options = options;
    }
    
    public WorkflowService.Iface build() {
        return new WorkflowServiceTChannel(host, port, options);
    }
}
