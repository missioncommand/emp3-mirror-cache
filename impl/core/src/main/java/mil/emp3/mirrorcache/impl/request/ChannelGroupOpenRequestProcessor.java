package mil.emp3.mirrorcache.impl.request;

import org.cmapi.primitives.proto.CmapiProto.ChannelGroupOpenOperation;
import org.cmapi.primitives.proto.CmapiProto.OneOfOperation;
import org.cmapi.primitives.proto.CmapiProto.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mil.emp3.mirrorcache.Message;
import mil.emp3.mirrorcache.MessageDispatcher;
import mil.emp3.mirrorcache.MirrorCacheException;
import mil.emp3.mirrorcache.MirrorCacheException.Reason;
import mil.emp3.mirrorcache.Priority;

public class ChannelGroupOpenRequestProcessor extends BaseRequestProcessor<Message, Void> {
    static final private Logger LOG = LoggerFactory.getLogger(ChannelGroupOpenRequestProcessor.class);
    
    final private MessageDispatcher dispatcher;
    
    public ChannelGroupOpenRequestProcessor(MessageDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }
    
    @Override
    public Void executeSync(Message reqMessage) throws MirrorCacheException {
        if (reqMessage == null) {
            throw new IllegalStateException("reqMessage == null");
        }
        
        dispatcher.dispatchMessage(reqMessage.setPriority(Priority.MEDIUM));
        
        try {
            final Message resMessage = dispatcher.awaitResponse(reqMessage);
            
            final ChannelGroupOpenOperation operation = resMessage.getOperation().as(OneOfOperation.class).getChannelGroupOpen();
            if (!(operation.getStatus() == Status.SUCCESS)) {
                throw new MirrorCacheException(Reason.CHANNELGROUP_OPEN_FAILURE).withDetail("channelGroupName: " + operation.getChannelGroupName());
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.info(Thread.currentThread().getName() + " thread interrupted.");
        }
        
        return null;
    }
}
