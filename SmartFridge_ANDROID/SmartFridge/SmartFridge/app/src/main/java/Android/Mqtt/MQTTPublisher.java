package Android.Mqtt;

import Android.Logic.Logs;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MQTTPublisher {

    /**
     *
     * @param broker
     * @param topic
     * @param content
     */
    public static void publish(MQTTBroker broker, String topic, String content) {
        MemoryPersistence persistence = new MemoryPersistence();
        try {
            MqttClient sampleClient = new MqttClient(MQTTBroker.getBroker(), MQTTBroker.getClientId(), persistence);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setUserName(MQTTBroker.getUsername());
            connOpts.setPassword(MQTTBroker.getPassword().toCharArray());
            connOpts.setCleanSession(true);
            Logs.info("Database","Connecting to broker: " + MQTTBroker.getBroker());
            sampleClient.connect(connOpts);
            Logs.info("Database", "Connected");
            MqttMessage message = new MqttMessage(content.getBytes());
            message.setQos(MQTTBroker.getQos());
            sampleClient.publish(topic, message);
            Logs.info("Database: ","Message published");
            sampleClient.disconnect();
            Logs.info("Database: ","Disconnected");

        } catch (MqttException me) {
            Logs.error("Error on publishing value: {}", me.toString());
        } catch (Exception e) {
            Logs.error("Error on publishing value: {}", e.toString());
        }
    }
}
