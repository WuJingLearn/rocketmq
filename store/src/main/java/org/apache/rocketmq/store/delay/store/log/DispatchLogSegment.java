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

import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;
import org.apache.rocketmq.store.delay.base.SegmentBufferExtend;
import org.apache.rocketmq.store.delay.model.LogRecord;
import org.apache.rocketmq.store.delay.store.visitor.DispatchLogVisitor;
import org.apache.rocketmq.store.delay.store.visitor.LogVisitor;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class DispatchLogSegment extends AbstractDelaySegment<Boolean> {
    private static final InternalLogger LOGGER = InternalLoggerFactory.getLogger(LoggerName.STORE_LOGGER_NAME);

    DispatchLogSegment(File file) throws IOException {
        super(file);
    }

    @Override
    public void afterAppendSuccess(LogRecord log) {}

    @Override
    public long validate() throws IOException {
        long size = this.fileChannel.size();
        long invalidateBytes = size % Long.BYTES;
        return size - invalidateBytes;
    }

    public LogVisitor<Long> newVisitor(long from) {
        return new DispatchLogVisitor(from, fileChannel);
    }

    SegmentBufferExtend selectSegmentBuffer(long offset) {
        long wrotePosition = getWrotePosition();
        if (wrotePosition == 0) {
            return new SegmentBufferExtend(0, null, 0, getSegmentBaseOffset());
        }

        if (offset < wrotePosition && offset >= 0) {
            int size = (int) (wrotePosition - offset);
            final ByteBuffer buffer = ByteBuffer.allocate(size);
            try {
                int bytes = fileChannel.read(buffer, offset);
                if (bytes < size) {
                    LOGGER.error("select dispatch log incomplete data to log segment,{}-{}-{}, {} -> {}", getSegmentBaseOffset(), wrotePosition, offset, bytes, size);
                    return null;
                }

                buffer.flip();
                buffer.limit(bytes);
                return new SegmentBufferExtend(offset, buffer, bytes, getSegmentBaseOffset());
            } catch (Throwable e) {
                LOGGER.error("select dispatch log data to log segment failed.", e);
            }
        }

        return null;
    }

    boolean appendData(long startOffset, ByteBuffer body) {
        long currentPos = getWrotePosition();
        int size = body.limit();
        if (startOffset != currentPos) {
            return false;
        }

        try {
            fileChannel.position(currentPos);
            fileChannel.write(body);
        } catch (Throwable e) {
            LOGGER.error("appendMessageLog data to log segment failed.", e);
            return false;
        }

        setWrotePosition(currentPos + size);
        return true;
    }

    void fillPreBlank(long untilWhere) {
        setWrotePosition(untilWhere);
    }

    public int entries() {
        try {
            return (int) (validate() / Long.BYTES);
        } catch (Exception e) {
            return 0;
        }
    }
}
