package Android.Mqtt;

/*import org.eclipse.paho.android.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;*/
import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;


public class MQTTBroker {

    private static int qos = 2;
    private static final String broker = "tcp://localhost:1883";
    private static final String clientId = "WheatherStationUAH";
    private static final String username = "ubicua";
    private static final String password = "ubicua";
    
    private static MQTTBroker instance;
    private MqttClient client;

    private MQTTBroker() {
        try {
            client = new MqttClient(broker, clientId, new MemoryPersistence());
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setUserName(username);
            connOpts.setPassword(password.toCharArray());
            connOpts.setCleanSession(true);
            client.connect(connOpts);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public static synchronized MQTTBroker getInstance() {
        if (instance == null) {
            instance = new MQTTBroker();
        }
        return instance;
    }

    public MqttClient getClient() {
        return client;
    }

    public static int getQos() {
        return qos;
    }

    public static String getBroker() {
        return broker;
    }

    public static String getClientId() {
        return clientId;
    }

    public static String getUsername() {
        return password;
    }

    public static String getPassword() {
        return password;
    }
}
