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

package org.apache.rocketmq.store.delay.store;

import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;
import org.apache.rocketmq.store.delay.appender.LogAppender;
import org.apache.rocketmq.store.delay.model.*;
import org.apache.rocketmq.store.delay.validator.DelaySegmentValidator;

import java.io.File;
import java.util.concurrent.ConcurrentSkipListMap;


public abstract class AbstractDelaySegmentContainer<T> implements SegmentContainer<RecordResult<T>, LogRecord> {
    private static final InternalLogger LOGGER = InternalLoggerFactory.getLogger(LoggerName.STORE_LOGGER_NAME);

    File logDir;

    private final LogAppender<T, LogRecord> appender;

    final int segmentScale;

    final ConcurrentSkipListMap<Long, DelaySegment<T>> segments = new ConcurrentSkipListMap<>();

    AbstractDelaySegmentContainer(int scale, File logDir, DelaySegmentValidator validator, LogAppender<T, LogRecord> appender) {
        this.segmentScale = scale;
        this.logDir = logDir;
        this.appender = appender;
        createAndValidateLogDir();
        loadLogs(validator);
    }

    protected abstract void loadLogs(DelaySegmentValidator validator);

    private void createAndValidateLogDir() {
        if (!logDir.exists()) {
            LOGGER.info("Log directory {} not found, try create it.", logDir.getAbsoluteFile());
            boolean created = logDir.mkdirs();
            if (!created) {
                throw new RuntimeException("Failed to create log directory " + logDir.getAbsolutePath());
            }
        }

        if (!logDir.isDirectory() || !logDir.canRead() || !logDir.canWrite()) {
            throw new RuntimeException(logDir.getAbsolutePath() + " is not a readable log directory");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public RecordResult<T> append(LogRecord record) {
        long scheduleTime = record.getScheduleTime();
        DelaySegment<T> segment = locateSegment(scheduleTime);
        if (null == segment) {
            segment = allocNewSegment(scheduleTime);
        }

        if (null == segment) {
            return new NopeRecordResult(PutMessageStatus.CREATE_MAPPED_FILE_FAILED);
        }

        return retResult(segment.append(record, appender));
    }

    @Override
    public boolean clean(Long key) {
        if (segments.isEmpty()) return false;
        if (segments.lastKey() < key) return false;
        DelaySegment segment = segments.remove(key);
        if (null == segment) {
            LOGGER.error("clean delay segment log failed,segment:{}", logDir, key);
            return false;
        }

        if (!segment.destroy()) {
            LOGGER.warn("remove delay segment failed.segment:{}", segment);
            return false;
        }

        LOGGER.info("remove delay segment success.segment:{}", segment);
        return true;
    }

    @Override
    public void flush() {
        for (DelaySegment<T> segment : segments.values()) {
            segment.flush();
        }
    }

    protected abstract RecordResult<T> retResult(AppendMessageResult<T> result);

    DelaySegment<T> locateSegment(long scheduleTime) {
        long baseOffset = ScheduleOffsetResolver.resolveSegment(scheduleTime, segmentScale);
        return segments.get(baseOffset);
    }

    private DelaySegment<T> allocNewSegment(long offset) {
        long baseOffset = ScheduleOffsetResolver.resolveSegment(offset, segmentScale);
        if (segments.containsKey(baseOffset)) {
            return segments.get(baseOffset);
        }
        return allocSegment(baseOffset);
    }

    long higherBaseOffset(long low) {
        Long next = segments.higherKey(low);
        return next == null ? -1 : next;
    }

    protected abstract DelaySegment<T> allocSegment(long segmentBaseOffset);
}
