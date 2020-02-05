/*
 * Copyright 2018 Qunar, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.rocketmq.store.delay.store.log;

import org.apache.rocketmq.store.delay.store.PeriodicFlushService;
import org.apache.rocketmq.store.delay.store.appender.DispatchLogAppender;
import org.apache.rocketmq.store.delay.base.SegmentBuffer;
import org.apache.rocketmq.store.delay.cleaner.LogCleaner;
import org.apache.rocketmq.store.delay.config.DelayMessageStoreConfiguration;
import org.apache.rocketmq.store.delay.store.validator.DefaultDelaySegmentValidator;

import java.io.File;
import java.nio.ByteBuffer;

public class DispatchLog extends AbstractDelayLog<Boolean> {
    /**
     * log flush interval,500ms
     */
    private static final int DEFAULT_FLUSH_INTERVAL = 500;

    public DispatchLog(DelayMessageStoreConfiguration storeConfiguration) {
        super(new DispatchLogSegmentContainer(storeConfiguration,
                new File(storeConfiguration.getDispatchLogStorePath())
                , new DefaultDelaySegmentValidator(), new DispatchLogAppender()));
    }

    public PeriodicFlushService.FlushProvider getProvider() {
        return new PeriodicFlushService.FlushProvider() {
            @Override
            public int getInterval() {
                return DEFAULT_FLUSH_INTERVAL;
            }

            @Override
            public void flush() {
                DispatchLog.this.flush();
            }
        };
    }

    public DispatchLogSegment latestSegment() {
        return ((DispatchLogSegmentContainer) container).latestSegment();
    }

    public void clean(LogCleaner.CleanHook hook) {
        ((DispatchLogSegmentContainer) container).clean(hook);
    }

    public SegmentBuffer getDispatchLogData(long segmentBaseOffset, long dispatchLogOffset) {
        return ((DispatchLogSegmentContainer) container).getDispatchData(segmentBaseOffset, dispatchLogOffset);
    }

    public long getMaxOffset(long dispatchSegmentBaseOffset) {
        return ((DispatchLogSegmentContainer) container).getMaxOffset(dispatchSegmentBaseOffset);
    }


    public boolean appendData(long startOffset, long baseOffset, ByteBuffer body) {
        return ((DispatchLogSegmentContainer) container).appendData(startOffset, baseOffset, body);
    }

    public DispatchLogSegment lowerSegment(long latestOffset) {
        return ((DispatchLogSegmentContainer) container).lowerSegment(latestOffset);
    }

    public long higherBaseOffset(long low) {
        return ((DispatchLogSegmentContainer) container).higherBaseOffset(low);
    }
}
