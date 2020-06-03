import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args)  {

        runConsumer();

    }

    static void runProducer() {
        int N = 10;
        Producer producer = Producer.getInstance();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Gracefully shutting down");
            producer.close();
        }));

        String topic = "log_position";
        String keyPrefix = "vehicle";

        for (int i = 0; i < N; i++) {
            producer.sendRecord(topic, keyPrefix + i, "location-" + i);
        }
    }

    static void runConsumer() {
        Consumer consumer = new Consumer();
        consumer.startListening("log_position");

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        consumer.stopConsumer();
        consumer.closeExecutor();

        logger.info("Main exits");
    }

}

