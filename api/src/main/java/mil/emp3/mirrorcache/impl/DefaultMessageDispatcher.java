package mil.emp3.mirrorcache.impl;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.cmapi.primitives.proto.CmapiProto.ChannelPublishCommand;
import org.cmapi.primitives.proto.CmapiProto.OneOfCommand.CommandCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mil.emp3.mirrorcache.Message;
import mil.emp3.mirrorcache.MessageDispatcher;
import mil.emp3.mirrorcache.MirrorCacheException;
import mil.emp3.mirrorcache.MirrorCacheException.Reason;
import mil.emp3.mirrorcache.RequestProcessor;
import mil.emp3.mirrorcache.channel.ChannelHandler;
import mil.emp3.mirrorcache.event.ClientConnectEvent;
import mil.emp3.mirrorcache.event.ClientDisconnectEvent;
import mil.emp3.mirrorcache.event.ClientEventHandler;
import mil.emp3.mirrorcache.event.ClientMessageEvent;
import mil.emp3.mirrorcache.event.EventHandler;
import mil.emp3.mirrorcache.event.EventRegistration;
import mil.emp3.mirrorcache.event.MirrorCacheEvent;
import mil.emp3.mirrorcache.impl.request.ChannelCacheRequestProcessor;
import mil.emp3.mirrorcache.impl.request.ChannelCloseRequestProcessor;
import mil.emp3.mirrorcache.impl.request.ChannelDeleteRequestProcessor;
import mil.emp3.mirrorcache.impl.request.ChannelGroupAddChannelRequestProcessor;
import mil.emp3.mirrorcache.impl.request.ChannelGroupCacheRequestProcessor;
import mil.emp3.mirrorcache.impl.request.ChannelGroupCloseRequestProcessor;
import mil.emp3.mirrorcache.impl.request.ChannelGroupDeleteRequestProcessor;
import mil.emp3.mirrorcache.impl.request.ChannelGroupOpenRequestProcessor;
import mil.emp3.mirrorcache.impl.request.ChannelGroupPublishRequestProcessor;
import mil.emp3.mirrorcache.impl.request.ChannelGroupRemoveChannelRequestProcessor;
import mil.emp3.mirrorcache.impl.request.ChannelOpenRequestProcessor;
import mil.emp3.mirrorcache.impl.request.ChannelPublishRequestProcessor;
import mil.emp3.mirrorcache.impl.request.CreateChannelGroupRequestProcessor;
import mil.emp3.mirrorcache.impl.request.CreateChannelRequestProcessor;
import mil.emp3.mirrorcache.impl.request.DeleteChannelGroupRequestProcessor;
import mil.emp3.mirrorcache.impl.request.DeleteChannelRequestProcessor;
import mil.emp3.mirrorcache.impl.request.FindChannelGroupsRequestProcessor;
import mil.emp3.mirrorcache.impl.request.FindChannelsRequestProcessor;
import mil.emp3.mirrorcache.impl.response.BaseResponseProcessor;
import mil.emp3.mirrorcache.impl.response.ChannelDeleteResponseProcessor;
import mil.emp3.mirrorcache.impl.response.ChannelGroupDeleteResponseProcessor;
import mil.emp3.mirrorcache.impl.response.ChannelGroupPublishResponseProcessor;
import mil.emp3.mirrorcache.impl.response.ChannelPublishResponseProcessor;
import mil.emp3.mirrorcache.impl.response.ResponseProcessor;
import mil.emp3.mirrorcache.impl.stage.DeserializeStageProcessor;
import mil.emp3.mirrorcache.impl.stage.InTransportStageProcessor;
import mil.emp3.mirrorcache.impl.stage.OutTransportStageProcessor;
import mil.emp3.mirrorcache.impl.stage.SerializeStageProcessor;
import mil.emp3.mirrorcache.impl.stage.TranslateStageProcessor;
import mil.emp3.mirrorcache.spi.ChannelHandlerProviderFactory;
import mil.emp3.mirrorcache.support.LatchedContent;

public class DefaultMessageDispatcher implements MessageDispatcher {
    static final private Logger LOG = LoggerFactory.getLogger(DefaultMessageDispatcher.class);
    
    final private Map<MirrorCacheEvent.Type<?>, List<EventHandler>> eventHandlerMap;
    
    final private Chain outProcessingPipeline;
    final private Chain inProcessingPipeline;
    
    final private Map<CommandCase, ConcurrentMap<String, LatchedContent<Message>>> responseQueueMap;
    final private Map<CommandCase, List<ResponseProcessor>> responseProcessorMap; //NOTE Multiple ResponseHandlers may register for the same Command
    final private Map<Class<? extends RequestProcessor<?, ?>>, RequestProcessor<?, ?>> requestProcessorMap;
    
    final private LocalResponseProcessor localResponseProcessor;
    final private DefaultMirrorCacheClient client;
    
    // -+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+- //
    // -+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+- //
    
    public DefaultMessageDispatcher(DefaultMirrorCacheClient client) {
        this.client = client;
        
        this.outProcessingPipeline = new Chain();
        this.inProcessingPipeline  = new Chain();
        this.eventHandlerMap       = new HashMap<>();
        
        this.responseQueueMap = new EnumMap<>(CommandCase.class);
        this.responseQueueMap.put(CommandCase.CREATE_CHANNEL , new ConcurrentHashMap<String, LatchedContent<Message>>());
        this.responseQueueMap.put(CommandCase.DELETE_CHANNEL , new ConcurrentHashMap<String, LatchedContent<Message>>());
        this.responseQueueMap.put(CommandCase.FIND_CHANNELS  , new ConcurrentHashMap<String, LatchedContent<Message>>());
        this.responseQueueMap.put(CommandCase.CHANNEL_OPEN   , new ConcurrentHashMap<String, LatchedContent<Message>>());
        this.responseQueueMap.put(CommandCase.CHANNEL_CLOSE  , new ConcurrentHashMap<String, LatchedContent<Message>>());
        this.responseQueueMap.put(CommandCase.CHANNEL_DELETE , new ConcurrentHashMap<String, LatchedContent<Message>>());
        this.responseQueueMap.put(CommandCase.CHANNEL_CACHE  , new ConcurrentHashMap<String, LatchedContent<Message>>());
        this.responseQueueMap.put(CommandCase.CHANNEL_HISTORY, new ConcurrentHashMap<String, LatchedContent<Message>>());
//        this.responseQueueMap.put(CommandCase.CHANNEL_PUBLISH, new ConcurrentHashMap<String, LatchedContainer<Message>>());
        
        this.responseQueueMap.put(CommandCase.CREATE_CHANNEL_GROUP        , new ConcurrentHashMap<String, LatchedContent<Message>>());
        this.responseQueueMap.put(CommandCase.DELETE_CHANNEL_GROUP        , new ConcurrentHashMap<String, LatchedContent<Message>>());
        this.responseQueueMap.put(CommandCase.FIND_CHANNEL_GROUPS         , new ConcurrentHashMap<String, LatchedContent<Message>>());
        this.responseQueueMap.put(CommandCase.CHANNEL_GROUP_OPEN          , new ConcurrentHashMap<String, LatchedContent<Message>>());
        this.responseQueueMap.put(CommandCase.CHANNEL_GROUP_CLOSE         , new ConcurrentHashMap<String, LatchedContent<Message>>());
        this.responseQueueMap.put(CommandCase.CHANNEL_GROUP_ADD_CHANNEL   , new ConcurrentHashMap<String, LatchedContent<Message>>());
        this.responseQueueMap.put(CommandCase.CHANNEL_GROUP_REMOVE_CHANNEL, new ConcurrentHashMap<String, LatchedContent<Message>>());
        this.responseQueueMap.put(CommandCase.CHANNEL_GROUP_DELETE        , new ConcurrentHashMap<String, LatchedContent<Message>>());
        this.responseQueueMap.put(CommandCase.CHANNEL_GROUP_CACHE         , new ConcurrentHashMap<String, LatchedContent<Message>>());
        this.responseQueueMap.put(CommandCase.CHANNEL_GROUP_HISTORY       , new ConcurrentHashMap<String, LatchedContent<Message>>());
//        this.responseQueueMap.put(CommandCase.CHANNEL_GROUP_PUBLISH       , new ConcurrentHashMap<String, LatchedContainer<Message>>());
        
        this.responseProcessorMap = new EnumMap<>(CommandCase.class);
        responseProcessorMap.put(CommandCase.CHANNEL_PUBLISH      , new ArrayList<ResponseProcessor>() {{ add(new ChannelPublishResponseProcessor(DefaultMessageDispatcher.this)); }});
        responseProcessorMap.put(CommandCase.CHANNEL_DELETE       , new ArrayList<ResponseProcessor>() {{ add(new ChannelDeleteResponseProcessor(DefaultMessageDispatcher.this)); }});
        responseProcessorMap.put(CommandCase.CHANNEL_GROUP_PUBLISH, new ArrayList<ResponseProcessor>() {{ add(new ChannelGroupPublishResponseProcessor(DefaultMessageDispatcher.this)); }});
        responseProcessorMap.put(CommandCase.CHANNEL_GROUP_DELETE , new ArrayList<ResponseProcessor>() {{ add(new ChannelGroupDeleteResponseProcessor(DefaultMessageDispatcher.this)); }});
        
        this.requestProcessorMap = new HashMap<>();
        requestProcessorMap.put(CreateChannelRequestProcessor.class            , new CreateChannelRequestProcessor(this));
        requestProcessorMap.put(DeleteChannelRequestProcessor.class            , new DeleteChannelRequestProcessor(this));
        requestProcessorMap.put(FindChannelsRequestProcessor.class             , new FindChannelsRequestProcessor(this));
        requestProcessorMap.put(ChannelOpenRequestProcessor.class              , new ChannelOpenRequestProcessor(this));
        requestProcessorMap.put(ChannelCloseRequestProcessor.class             , new ChannelCloseRequestProcessor(this));
        requestProcessorMap.put(ChannelPublishRequestProcessor.class           , new ChannelPublishRequestProcessor(this));
        requestProcessorMap.put(ChannelDeleteRequestProcessor.class            , new ChannelDeleteRequestProcessor(this));
        requestProcessorMap.put(ChannelCacheRequestProcessor.class             , new ChannelCacheRequestProcessor(this));
//        requestProcessorMap.put(ChannelHistoryRequestProcessor.class           , new ChannelHistoryRequestProcessor(this));
        
        requestProcessorMap.put(CreateChannelGroupRequestProcessor.class       , new CreateChannelGroupRequestProcessor(this));
        requestProcessorMap.put(DeleteChannelGroupRequestProcessor.class       , new DeleteChannelGroupRequestProcessor(this));
        requestProcessorMap.put(FindChannelGroupsRequestProcessor.class        , new FindChannelGroupsRequestProcessor(this));
        requestProcessorMap.put(ChannelGroupAddChannelRequestProcessor.class   , new ChannelGroupAddChannelRequestProcessor(this));
        requestProcessorMap.put(ChannelGroupRemoveChannelRequestProcessor.class, new ChannelGroupRemoveChannelRequestProcessor(this));
        requestProcessorMap.put(ChannelGroupOpenRequestProcessor.class         , new ChannelGroupOpenRequestProcessor(this));
        requestProcessorMap.put(ChannelGroupCloseRequestProcessor.class        , new ChannelGroupCloseRequestProcessor(this));
        requestProcessorMap.put(ChannelGroupPublishRequestProcessor.class      , new ChannelGroupPublishRequestProcessor(this));
        requestProcessorMap.put(ChannelGroupDeleteRequestProcessor.class       , new ChannelGroupDeleteRequestProcessor(this));
        requestProcessorMap.put(ChannelGroupCacheRequestProcessor.class        , new ChannelGroupCacheRequestProcessor(this));
//        requestProcessorMap.put(ChannelGroupHistoryRequestProcessor.class      , new ChannelGroupHistoryRequestProcessor(this));
        
        this.localResponseProcessor = new LocalResponseProcessor();
    }
    
    // -+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+- //
    // -+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+- //

    public void init() {
        final TranslateStageProcessor translateStageProcessor = new TranslateStageProcessor();
        
        inProcessingPipeline.link(new InTransportStageProcessor())
                            .link(new DeserializeStageProcessor())
                            .link(translateStageProcessor);
        
        outProcessingPipeline.link(translateStageProcessor)
                             .link(new SerializeStageProcessor())
                             .link(new OutTransportStageProcessor());
        
        localResponseProcessor.init();
        
        for (List<ResponseProcessor> responseProcessors : responseProcessorMap.values()) {
            for (ResponseProcessor responseProcessor : responseProcessors) {
                responseProcessor.init();
            }
        }
        
        /*
         * The MessageDispatcher is interested in onMessage client events. Upon receiving one, the message object will
         * be extracted and sent to a queue where registered response processors may interrogate it. This is a non-blocking
         * call as the message queue will be consumed by the response processors in a different thread.
         */
        client.on(ClientMessageEvent.TYPE, new ClientEventHandler() {
            @Override public void onMessage(ClientMessageEvent event) {
                localResponseProcessor.processMessage(event.getMessage());
            }
            @Override public void onDisconnect(ClientDisconnectEvent event) { /* NOOP */ }
            @Override public void onConnect(ClientConnectEvent event) { /* NOOP */ }
        });
    }
    
    public void shutdown() {
        /*
         * Shutdown response processors.
         */
        for (List<ResponseProcessor> responseProcessors : responseProcessorMap.values()) {
            for (ResponseProcessor responseProcessor : responseProcessors) {
                responseProcessor.shutdown();
            }
        }
        
        /*
         * Shutdown the local response processor.
         */
        localResponseProcessor.shutdown();
        
        
        /*
         * Unregister all events.
         */
        eventHandlerMap.clear();
        

        /*
         * Shutdown processing pipelines.
         */
        outProcessingPipeline.shutdown();
        inProcessingPipeline.shutdown();
    }
    
    @Override
    public <T extends EventHandler> EventRegistration on(MirrorCacheEvent.Type<T> type, final T handler) {
        if (type == null || handler == null) {
            throw new IllegalStateException("type == null || handler == null");
        }
        
        final List<EventHandler> handlers;
        if (eventHandlerMap.get(type) == null) {
            eventHandlerMap.put(type, handlers = new ArrayList<EventHandler>());
        } else {
            handlers = eventHandlerMap.get(type);
        }
            
        handlers.add(handler);
        
        return new EventRegistration() {
            @Override public void removeHandler() {
                handlers.remove(handler);
            }
        };
    }

    /**
     * Blocks the current thread until a response message to the provided {@code reqMessage}
     * is received.
     * 
     * @param reqMessage the request message we are waiting for a response to.
     * @return the response message to the provided {@code reqMessage}
     * @throws MirrorCacheException If a we time out waiting for a response message or
     *                              if the response message contains an unexpected {@code id}.
     * @throws InterruptedException If an InterruptedException occurs.
     */
    @Override
    public Message awaitResponse(Message reqMessage) throws MirrorCacheException, InterruptedException {
        LOG.debug("awaitResponse() : commandCase=" + reqMessage.getCommand().getCommandCase());
//TODO consider using CompletionService instead..
        
        final CommandCase command = reqMessage.getCommand().getCommandCase();
        final String reqId        = reqMessage.getId();

        LatchedContent<Message> resBucket = responseQueueMap.get(command).putIfAbsent(reqId, new LatchedContent<Message>(1));
        if (resBucket == null) {
            resBucket = responseQueueMap.get(command).get(reqId);
        }

        if (!resBucket.await(reqId, 5, TimeUnit.SECONDS)) {
            throw new MirrorCacheException(Reason.QUEUE_POLL_TIMEOUT).withDetail("QUEUE: " + command);
        }
        
        final Message resMessage = resBucket.getContent();
        if (resMessage == null) {
            throw new IllegalStateException("resMessage == null");
        }

        if (!resMessage.getId().equals(reqId)) {
            throw new MirrorCacheException(Reason.QUEUE_UNEXPECTED_ID).withDetail("Expected reqId: " + reqId)
                                                                      .withDetail("Actual reqId: " + resMessage.getId());
        }
        
        if (!responseQueueMap.get(command).remove(reqId, resBucket)) {
            throw new IllegalStateException("remove returned false");
        }
        
        return resMessage;
    }
    
    public Chain getOutProcessorPipeline() {
        return outProcessingPipeline;
    }
    
    @Override
    public Chain getInProcessorPipeline() {
        return inProcessingPipeline;
    }
    
    /**
     * Dispatches events to registered handlers synchronously.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void dispatchEvent(MirrorCacheEvent event) {
        if (eventHandlerMap.containsKey(event.getType())) {
            for (EventHandler handler : eventHandlerMap.get(event.getType())) {
                event.dispatch(handler);
            }
        }
    }
    
    @Override
    public void dispatchMessage(Message message) throws MirrorCacheException {
        getOutProcessorPipeline().processMessage(message);
        
        client.getTransport().send(message);
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <T extends RequestProcessor<?, ?>> T getRequestProcessor(Class<T> clazz) {
        final T requestProcessor = (T) requestProcessorMap.get(clazz);
        if (requestProcessor == null) {
            throw new IllegalStateException("Unregistered requestProcessor: " + clazz);
        }
        return requestProcessor;
    }

    
    // -+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+- //
    // -+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+- //

    
    static private boolean isResponseExpected(CommandCase command) {
        return    command != CommandCase.CHANNEL_PUBLISH
               && command != CommandCase.CHANNEL_GROUP_PUBLISH;
    }
    
    // -+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+- //
    // -+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+- //

    private class LocalResponseProcessor extends BaseResponseProcessor {
        public LocalResponseProcessor() {
            super(DefaultMessageDispatcher.this, "MessageDispatcher.LocalResponseProcessor", 5);
        }
        
        @Override
        public void onMessage(Message message) throws MirrorCacheException {
            /*
             * Opportunity to react to responses.
             */
            List<ResponseProcessor> responseProcessors = responseProcessorMap.get(message.getCommand().getCommandCase());
            if (responseProcessors != null) {
                for (ResponseProcessor responseProcessor : responseProcessors) {
                    responseProcessor.processMessage(message);
                }
            }

            /*
             * If applicable, provide response data back to originating request processor. 
             */
            if (isResponseExpected(message.getCommand().getCommandCase())) {
                final CommandCase command = message.getCommand().getCommandCase();
                final String resId        = message.getId();
                
                LatchedContent<Message> queue = responseQueueMap.get(command).putIfAbsent(resId, new LatchedContent<Message>(1));
                if (queue == null) {
                    queue = responseQueueMap.get(command).get(resId);
                }
                queue.setContent(message);
            }
            //TODO (memleak) we must remove the consumed resIds from the responseQueueMap, or those older than some interval.
            
            /*
             * Invoke Channel Handlers
             */
            final List<ChannelHandler> channelHandlers = new ArrayList<>();
            
            if (CommandCase.CHANNEL_PUBLISH == message.getCommand().getCommandCase()) {
                final ChannelPublishCommand command = message.getCommand(CommandCase.CHANNEL_PUBLISH);
                channelHandlers.addAll(ChannelHandlerProviderFactory.getChannelHandlers(command.getChannelName()));
                
            } else if (CommandCase.CHANNEL_GROUP_PUBLISH == message.getCommand().getCommandCase()) {
//TODO
//                final ChannelGroupPublishCommand command = message.getCommand(CommandCase.CHANNEL_GROUP_PUBLISH);
//                channelHandlers.addAll(ChannelHandlerProviderFactory.getChannelHandlers(command.getChannelGroupName()));
            }
            
            for (ChannelHandler handler : channelHandlers) {
                handler.processMessage(message);
            }
            
        }
    }
}