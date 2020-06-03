import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.*;

public class Consumer {

    private final static Logger logger = LoggerFactory.getLogger(Consumer.class);

    private final KafkaConsumer<String, String> consumer;
    private final ExecutorService executor;
    private final CountDownLatch latch;

    public Consumer() {
        consumer = new KafkaConsumer <>(getDefaultProperties());
        latch = new CountDownLatch(1);
        executor = Executors.newSingleThreadExecutor();
    }

    private Properties getDefaultProperties() {
        Properties properties = new Properties();
        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "myConsumerGroup-7");
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return properties;
    }

    public void startListening(String topic) {
        logger.info("Starts Listen Task");
        executor.submit(getListenTask(topic));
    }

    private Runnable getListenTask(String topic) {
        return () -> {
            addShutDownHook();
            consumer.subscribe(Collections.singletonList(topic));

            try {
                while (true) {
                    pollRecords();

                    consumer.commitAsync((offsets, exception) -> {
                        if (exception != null) {
                            logger.error("Commit Async Failed with exception: {}", exception);
                        }
                    });
                }
            } catch (WakeupException ex) {
                logger.error("Wake up received");
            } finally {
                consumer.close();
                logger.info("consumer is closed");
                latch.countDown();
            }
        };
    }

    private void pollRecords() {
        logger.info("polling");
        ConsumerRecords <String, String> records = consumer.poll(Duration.ofMillis(200));
        records.forEach(record -> {
            logger.info(record.toString());
            logger.info("Position is: {}", consumer.position(new TopicPartition("log_position", record.partition())));
        });


    }

    private void addShutDownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown is caught");
            stopConsumer();
            closeExecutor();
        }));
    }

    public void stopConsumer()  {
        consumer.wakeup();
        try {
            latch.await();
        } catch (InterruptedException e) {
            logger.error("Barrier wait is interrupted");
        }
    }

    public void closeExecutor() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.error("Could not shutdown executor");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
