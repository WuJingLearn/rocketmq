package org.apache.rocketmq.store.delay;

import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;
import org.apache.rocketmq.store.DefaultMessageStore;
import org.apache.rocketmq.store.DispatchRequest;
import org.apache.rocketmq.store.SelectMappedBufferResult;
import org.apache.rocketmq.store.config.MessageStoreConfig;
import org.apache.rocketmq.store.delay.config.DelayMessageStoreConfiguration;
import org.apache.rocketmq.store.delay.model.*;
import org.apache.rocketmq.store.delay.wheel.WheelTickManager;

public class DelayMessageService {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getLogger(LoggerName.STORE_LOGGER_NAME);

    public static final String DELAY_TOPIC = "DELAY_TOPIC_XXXX";

    private DelayMessageStoreConfiguration storeConfig;

    private MessageStoreConfig messageStoreConfig;

    private DefaultMessageStore defaultMessageStore;

    private DelayLogFacade delayLogFacade;

    private WheelTickManager wheelTickManager;

    public DelayMessageService(MessageStoreConfig messageStoreConfig, DefaultMessageStore messageStore) {
        this.messageStoreConfig = messageStoreConfig;
        this.defaultMessageStore = messageStore;
        this.storeConfig = new DelayMessageStoreConfiguration(messageStoreConfig);
        this.delayLogFacade = new DefaultDelayLogFacade(this.storeConfig);
        this.wheelTickManager = new WheelTickManager(this.storeConfig, this.delayLogFacade, this.defaultMessageStore);
    }

    public void start() {
        wheelTickManager.start();
        delayLogFacade.start();
//		sync();
    }


    public void shutdown() {
        delayLogFacade.shutdown();
        wheelTickManager.shutdown();
    }


    public void buildScheduleLog(DispatchRequest request) {

        SelectMappedBufferResult result = defaultMessageStore.getCommitLog().getData(request.getCommitLogOffset());

        long scheduleTime = request.getStoreTimestamp() + Long.parseLong(request.getPropertiesMap().get(MessageConst.PROPERTY_DELAY_TIME)) * 1000;

        String subject = request.getTopic();
        String messageId = request.getUniqKey();
        long sequence = request.getConsumeQueueOffset();
        LogRecordHeader header = new LogRecordHeader(subject, messageId, scheduleTime, sequence);

        LogRecord record = new MessageLogRecord(header, result.getSize(), result.getStartOffset(), result.getSize(), result.getByteBuffer());

        AppendLogResult<ScheduleIndex> appendLogResult  = delayLogFacade.appendScheduleLog(record);

        if (MessageProducerCode.SUCCESS != appendLogResult.getCode()) {
            LOGGER.error("Append schedule log error,log:{} {},code:{}", record.getSubject(), record.getMessageId(), appendLogResult.getCode());
        } else {
            addWheeel(appendLogResult.getAdditional());
        }



    }

    private boolean addWheeel(final ScheduleIndex index) {
        long scheduleTime = index.getScheduleTime();
        long offset = index.getOffset();
        if (wheelTickManager.canAdd(scheduleTime, offset)) {
            wheelTickManager.addWheel(index);
            return true;
        }
        return false;
    }

}
