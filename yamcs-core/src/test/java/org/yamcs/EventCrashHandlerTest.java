package org.yamcs;

import static org.junit.Assert.assertEquals;

import java.util.Queue;

import org.junit.Test;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.utils.TimeEncoding;

public class EventCrashHandlerTest {

    @Test
    public void sendErrorEventOk() {
        TimeEncoding.setUp();
        EventProducerFactory.setMockup(true);
        Queue<Event> eventQueue = EventProducerFactory.getMockupQueue();
        EventCrashHandler crashHandler = new EventCrashHandler("unitTestInstance");
        crashHandler.handleCrash("m1", "err1");
        crashHandler.handleCrash("m1", "err2");

        assertEquals(2, eventQueue.size());
    }
}
