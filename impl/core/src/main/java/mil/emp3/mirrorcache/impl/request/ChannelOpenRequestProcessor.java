package mil.emp3.mirrorcache.impl.request;

import org.cmapi.primitives.proto.CmapiProto.ChannelOpenCommand;
import org.cmapi.primitives.proto.CmapiProto.OneOfCommand;
import org.cmapi.primitives.proto.CmapiProto.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mil.emp3.mirrorcache.Message;
import mil.emp3.mirrorcache.MessageDispatcher;
import mil.emp3.mirrorcache.MirrorCacheException;
import mil.emp3.mirrorcache.MirrorCacheException.Reason;
import mil.emp3.mirrorcache.Priority;

public class ChannelOpenRequestProcessor extends BaseRequestProcessor<Message, Void> {
    static final private Logger LOG = LoggerFactory.getLogger(ChannelOpenRequestProcessor.class);
    
    final private MessageDispatcher dispatcher;
    
    public ChannelOpenRequestProcessor(MessageDispatcher dispatcher) {
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
            
            final ChannelOpenCommand command = resMessage.getOperation().as(OneOfCommand.class).getChannelOpen();
            if (!(command.getStatus() == Status.SUCCESS)) {
                throw new MirrorCacheException(Reason.CHANNEL_OPEN_FAILURE).withDetail("channelName: " + command.getChannelName());
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.info(Thread.currentThread().getName() + " thread interrupted.");
        }
        
        return null;
    }
}