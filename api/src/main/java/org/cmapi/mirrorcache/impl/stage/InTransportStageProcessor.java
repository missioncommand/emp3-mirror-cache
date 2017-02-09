package org.cmapi.mirrorcache.impl.stage;

import org.cmapi.mirrorcache.Message;
import org.cmapi.mirrorcache.MirrorCacheException;
import org.cmapi.mirrorcache.MirrorCacheException.Reason;
import org.cmapi.mirrorcache.Payload;
import org.cmapi.mirrorcache.Priority;
import org.cmapi.mirrorcache.support.Utils;
import org.cmapi.primitives.proto.CmapiProto.ProtoMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.InvalidProtocolBufferException;

public class InTransportStageProcessor extends StageProcessor<Message> {
    static final private Logger LOG = LoggerFactory.getLogger(InTransportStageProcessor.class);
    
    public InTransportStageProcessor() {
        super(Stage.IN_TRANSPORT);
    }
    
    @Override
    public void processMessage(Message message) throws MirrorCacheException {
        LOG.trace("processMessage()");
        
        final byte[] payload = message.getPayload(byte[].class).getData();
        
        final ProtoMessage pm;
        try {
            pm = ProtoMessage.parseFrom(payload);
            
            message.setId(pm.getId())
                   .setPriority(Priority.fromValue(pm.getPriority()))
                   .setCommand(pm.getCommand())
                   .setProperties(pm.getPropertyMap())
                   .setPayload(null);
            
            if (pm.hasPayload()) {
                message.setPayload(new Payload<>(pm.getPayload().getId(),
                                                 pm.getPayload().getType(),
                                                 pm.getPayload().getData().toByteArray()));
            }
            
        } catch (InvalidProtocolBufferException e) {
            throw new MirrorCacheException(Reason.TRANSPORT_FAILURE, e);
        }
        
        LOG.debug("inMessage(" + payload.length + "): " + Utils.asString(pm));
    }

}