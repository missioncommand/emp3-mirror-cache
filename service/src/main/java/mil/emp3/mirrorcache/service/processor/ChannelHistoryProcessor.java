package mil.emp3.mirrorcache.service.processor;

import java.util.concurrent.TimeUnit;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.cmapi.primitives.proto.CmapiProto.ChannelHistoryOperation;
import org.cmapi.primitives.proto.CmapiProto.HistoryInfo;
import org.cmapi.primitives.proto.CmapiProto.OneOfOperation;
import org.cmapi.primitives.proto.CmapiProto.ProtoMessage;
import org.cmapi.primitives.proto.CmapiProto.Status;
import org.slf4j.Logger;

import mil.emp3.mirrorcache.MirrorCacheException.Reason;
import mil.emp3.mirrorcache.Priority;
import mil.emp3.mirrorcache.service.HistoryManager;
import mil.emp3.mirrorcache.service.SessionManager;
import mil.emp3.mirrorcache.service.support.ProtoMessageEntry;

@ApplicationScoped
public class ChannelHistoryProcessor implements OperationProcessor {

    @Inject
    private Logger LOG;
    
    @Inject
    private HistoryManager historyManager;
    
    @Inject
    private SessionManager sessionManager;

    
    @Override
    public void process(String sessionId, ProtoMessage req) {
        final ChannelHistoryOperation operation = req.getOperation().getChannelHistory();

        final HistoryInfo history = historyManager.getChannelHistory(sessionId,
                                                                     operation.getChannelName(),
                                                                     operation.getStartTime(),
                                                                     operation.getEndTime());
        
        final ProtoMessage res = ProtoMessage.newBuilder(req)
                                             .setPriority(Priority.MEDIUM.getValue())
                                             .setOperation(OneOfOperation.newBuilder()
                                                                         .setChannelHistory(ChannelHistoryOperation.newBuilder(operation)
                                                                                                                   .setStatus(Status.SUCCESS)
                                                                                                                   .setHistory(history)))
                                             .build();
        
        try {
            if (!sessionManager.getOutboundQueue(sessionId).offer(new ProtoMessageEntry(res), 1, TimeUnit.SECONDS)) {
                throw new RuntimeException(Reason.QUEUE_OFFER_TIMEOUT.getMsg() + ", sessionId: " + sessionId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn(Thread.currentThread().getName() + " thread was interrupted.");
        }
    }

}
