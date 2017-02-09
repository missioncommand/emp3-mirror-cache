package org.cmapi.mirrorcache.impl;

import java.net.URI;
import java.util.List;
import java.util.concurrent.Future;

import org.cmapi.mirrorcache.Message;
import org.cmapi.mirrorcache.MirrorCacheClient;
import org.cmapi.mirrorcache.MirrorCacheException;
import org.cmapi.mirrorcache.Transport;
import org.cmapi.mirrorcache.channel.Channel;
import org.cmapi.mirrorcache.channel.ChannelGroup;
import org.cmapi.mirrorcache.event.EventHandler;
import org.cmapi.mirrorcache.event.EventRegistration;
import org.cmapi.mirrorcache.event.MirrorCacheEvent;
import org.cmapi.mirrorcache.impl.request.CreateChannelGroupRequestProcessor;
import org.cmapi.mirrorcache.impl.request.CreateChannelRequestProcessor;
import org.cmapi.mirrorcache.impl.request.DeleteChannelGroupRequestProcessor;
import org.cmapi.mirrorcache.impl.request.DeleteChannelRequestProcessor;
import org.cmapi.mirrorcache.impl.request.FindChannelGroupsRequestProcessor;
import org.cmapi.mirrorcache.impl.request.FindChannelsRequestProcessor;
import org.cmapi.primitives.proto.CmapiProto.CreateChannelCommand;
import org.cmapi.primitives.proto.CmapiProto.CreateChannelGroupCommand;
import org.cmapi.primitives.proto.CmapiProto.DeleteChannelCommand;
import org.cmapi.primitives.proto.CmapiProto.DeleteChannelGroupCommand;
import org.cmapi.primitives.proto.CmapiProto.FindChannelGroupsCommand;
import org.cmapi.primitives.proto.CmapiProto.FindChannelsCommand;
import org.cmapi.primitives.proto.CmapiProto.OneOfCommand.CommandCase;

public class DefaultMirrorCacheClient implements MirrorCacheClient {

    final private MessageDispatcher messageDispatcher;
    final private Transport transport;
    
    // -+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+- //
    // -+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+- //

    public DefaultMirrorCacheClient(URI endpoint) {
        this.messageDispatcher = new MessageDispatcher(this);
        this.transport         = new WebSocketClientTransport(endpoint, messageDispatcher); 
    }
    
    // -+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+- //
    // -+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+- //
    
    @Override
    public void init() {
        getMessageDispatcher().init();
    }
    
    @Override
    public void shutdown() throws MirrorCacheException {
        disconnect();
        getMessageDispatcher().shutdown();
    }
    
    @Override
    public void connect() throws MirrorCacheException {
        getTransport().connect();
    }

    @Override
    public void disconnect() throws MirrorCacheException {
        getTransport().disconnect();
    }
    
    @Override
    public <T extends EventHandler> EventRegistration on(MirrorCacheEvent.Type<T> type, T handler) {
        return getMessageDispatcher().on(type, handler);
    }

    @Override
    public Channel createChannel(String name, Channel.Visibility visibility, Channel.Type type) throws MirrorCacheException {
        final Message reqMessage = new Message();
        reqMessage.setCommand(CommandCase.CREATE_CHANNEL, CreateChannelCommand.newBuilder()
                                                                              .setChannelName(name)
                                                                              .setType(type.name())
                                                                              .setVisibility(visibility.name())
                                                                              .build());
        
        final Channel channel = getMessageDispatcher().getRequestProcessor(CreateChannelRequestProcessor.class).executeSync(reqMessage);
        return channel;
    }
    
    @Override
    public Future<Channel> createChannelAsync(final String name, final Channel.Visibility visibility, final Channel.Type type) {
        final Message reqMessage = new Message();
        reqMessage.setCommand(CommandCase.CREATE_CHANNEL, CreateChannelCommand.newBuilder()
                                                                              .setChannelName(name)
                                                                              .setType(type.name())
                                                                              .setVisibility(visibility.name())
                                                                              .build());
        
        final Future<Channel> futureChannel = getMessageDispatcher().getRequestProcessor(CreateChannelRequestProcessor.class).executeAsync(reqMessage);
        return futureChannel;
    }
    
    @Override
    public void deleteChannel(String name) throws MirrorCacheException {
        final Message reqMessage = new Message();
        reqMessage.setCommand(CommandCase.DELETE_CHANNEL, DeleteChannelCommand.newBuilder()
                                                                              .setChannelName(name)
                                                                              .build());
        
        getMessageDispatcher().getRequestProcessor(DeleteChannelRequestProcessor.class).executeSync(reqMessage);
    }

    @Override
    public Future<Void> deleteChannelAsync(String name) {
        final Message reqMessage = new Message();
        reqMessage.setCommand(CommandCase.DELETE_CHANNEL, DeleteChannelCommand.newBuilder()
                                                                              .setChannelName(name)
                                                                              .build());
        
        final Future<Void> futureVoid = getMessageDispatcher().getRequestProcessor(DeleteChannelRequestProcessor.class).executeAsync(reqMessage);
        return futureVoid;
    }

    @Override
    public List<Channel> findChannels(String filter) throws MirrorCacheException {
        final Message reqMessage = new Message();
        reqMessage.setCommand(CommandCase.FIND_CHANNELS, FindChannelsCommand.newBuilder()
                                                                            .setFilter(filter)
                                                                            .build());
        
        final List<Channel> channels = getMessageDispatcher().getRequestProcessor(FindChannelsRequestProcessor.class).executeSync(reqMessage);
        return channels;
    }
    
    @Override
    public Future<List<Channel>> findChannelsAsync(String filter) {
        final Message reqMessage = new Message();
        reqMessage.setCommand(CommandCase.FIND_CHANNELS, FindChannelsCommand.newBuilder()
                                                                            .setFilter(filter)
                                                                            .build());
        
        final Future<List<Channel>> futureChannels = getMessageDispatcher().getRequestProcessor(FindChannelsRequestProcessor.class).executeAsync(reqMessage);
        return futureChannels;
    }
    
    @Override
    public ChannelGroup createChannelGroup(String name) throws MirrorCacheException {
        final Message reqMessage = new Message();
        reqMessage.setCommand(CommandCase.CREATE_CHANNEL_GROUP, CreateChannelGroupCommand.newBuilder()
                                                                                         .setChannelGroupName(name)
                                                                                         .build());
        
        final ChannelGroup channelGroup = getMessageDispatcher().getRequestProcessor(CreateChannelGroupRequestProcessor.class).executeSync(reqMessage);
        return channelGroup;
    }
    
    @Override
    public Future<ChannelGroup> createChannelGroupAsync(String name) {
        final Message reqMessage = new Message();
        reqMessage.setCommand(CommandCase.CREATE_CHANNEL_GROUP, CreateChannelGroupCommand.newBuilder()
                                                                                         .setChannelGroupName(name)
                                                                                         .build());
        
        final Future<ChannelGroup> futureChannelGroup = getMessageDispatcher().getRequestProcessor(CreateChannelGroupRequestProcessor.class).executeAsync(reqMessage);
        return futureChannelGroup;
    }
    
    @Override
    public void deleteChannelGroup(String name) throws MirrorCacheException {
        final Message reqMessage = new Message();
        reqMessage.setCommand(CommandCase.DELETE_CHANNEL_GROUP, DeleteChannelGroupCommand.newBuilder()
                                                                                         .setChannelGroupName(name)
                                                                                         .build());
        
        getMessageDispatcher().getRequestProcessor(DeleteChannelGroupRequestProcessor.class).executeSync(reqMessage);
    }
    
    @Override
    public Future<Void> deleteChannelGroupAsync(String name) {
        final Message reqMessage = new Message();
        reqMessage.setCommand(CommandCase.DELETE_CHANNEL_GROUP, DeleteChannelGroupCommand.newBuilder()
                                                                                         .setChannelGroupName(name)
                                                                                         .build());
        
        final Future<Void> futureVoid = getMessageDispatcher().getRequestProcessor(DeleteChannelGroupRequestProcessor.class).executeAsync(reqMessage);
        return futureVoid;
    }
    
    @Override
    public List<ChannelGroup> findChannelGroups(String filter) throws MirrorCacheException {
        final Message reqMessage = new Message();
        reqMessage.setCommand(CommandCase.FIND_CHANNEL_GROUPS, FindChannelGroupsCommand.newBuilder()
                                                                                       .setFilter(filter)
                                                                                       .build());
        
        final List<ChannelGroup> channelGroups = getMessageDispatcher().getRequestProcessor(FindChannelGroupsRequestProcessor.class).executeSync(reqMessage);
        return channelGroups;
    }
    
    @Override
    public Future<List<ChannelGroup>> findChannelGroupsAsync(String filter) {
        final Message reqMessage = new Message();
        reqMessage.setCommand(CommandCase.FIND_CHANNEL_GROUPS, FindChannelGroupsCommand.newBuilder()
                                                                                       .setFilter(filter)
                                                                                       .build());
        
        final Future<List<ChannelGroup>> futureChannelGroups = getMessageDispatcher().getRequestProcessor(FindChannelGroupsRequestProcessor.class).executeAsync(reqMessage);
        return futureChannelGroups;
    }

    
    // -+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+- //
    // -+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+- //
    
    public Transport getTransport() {
        return transport;
    }
    
    private MessageDispatcher getMessageDispatcher() {
        return messageDispatcher;
    }
}