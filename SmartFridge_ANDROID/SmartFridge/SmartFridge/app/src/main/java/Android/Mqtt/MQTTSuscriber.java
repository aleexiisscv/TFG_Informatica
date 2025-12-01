package Android.Mqtt;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import Android.db.Topics;
import Android.Logic.Logs;

public class MQTTSuscriber implements MqttCallback {

    public void suscribeTopic(MQTTBroker broker, String topic) {
        Logs.debug("Database: ","Suscribe to topics");
        MemoryPersistence persistence = new MemoryPersistence();
        try {
            MqttClient sampleClient = new MqttClient(MQTTBroker.getBroker(), MQTTBroker.getClientId(), persistence);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setUserName(MQTTBroker.getUsername());
            connOpts.setPassword(MQTTBroker.getPassword().toCharArray());
            connOpts.setCleanSession(true);
            Logs.debug("Database: ","Mqtt Connecting to broker: " + MQTTBroker.getBroker());
            sampleClient.connect(connOpts);
            Logs.debug("Database: ","Mqtt Connected");
            sampleClient.setCallback(this);

            sampleClient.subscribe(topic);
            Logs.info("Subscribed to {}", topic);

        } catch (MqttException me) {
            Logs.error("Error suscribing topic: {}", me.toString());
        } catch (Exception e) {
            Logs.error("Error suscribing topic: {}", e.toString());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        Logs.info("{}: {}" +topic, message.toString());
        Topics newTopic = new Topics();
        newTopic.setValue(message.toString());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }
}
