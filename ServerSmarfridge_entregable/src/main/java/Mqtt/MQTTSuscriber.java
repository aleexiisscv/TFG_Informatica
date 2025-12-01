package mqtt;

import java.io.PrintWriter;
import java.sql.Connection;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;


import db.ConnectionDB;
import logic.Log;
import servlets.DatabaseServlet;

public class MQTTSuscriber implements MqttCallback {

    private String modo = ""; // Variable para almacenar el valor del modo

    public void suscribeTopic(MQTTBroker broker, String topic) {
        Log.logmqtt.debug("Suscribe to topics");
        MemoryPersistence persistence = new MemoryPersistence();
        try {
            MqttClient sampleClient = new MqttClient(MQTTBroker.getBroker(), MQTTBroker.getClientId(), persistence);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setUserName(MQTTBroker.getUsername());
            connOpts.setPassword(MQTTBroker.getPassword().toCharArray());
            connOpts.setCleanSession(true);
            Log.logmqtt.debug("Mqtt Connecting to broker: " + MQTTBroker.getBroker());
            sampleClient.connect(connOpts);
            Log.logmqtt.debug("se han suscrito al topic: " + topic);
            sampleClient.setCallback(this);

            sampleClient.subscribe(topic);
            Log.logmqtt.info("Subscribed to {}", topic);

        } catch (MqttException me) {
            Log.logmqtt.error("Error suscribing topic: {}", me);
        } catch (Exception e) {
            Log.logmqtt.error("Error suscribing topic: {}", e);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        ConnectionDB connectionDB = new ConnectionDB();
        Connection con = null;
        // Obtener conexión
        con = connectionDB.obtainConnection(true);
        switch (topic) {
            case "frigorifico/modo":
                Log.logmqtt.info("Modo actualizado: {}", message);
                DatabaseServlet.modo = message.toString();
                Log.logmqtt.info("Modo actualizado: {}", message);
                
                break;
            case "frigorifico/temperature":
                float temperature = Float.parseFloat(message.toString());
                ConnectionDB.actualizarUltimaMedicion(3, temperature, con);
                Log.logmqtt.info("Temperatura actualizada: {}", temperature);

                break;
            case "frigorifico/humidity":
                float humidity = Float.parseFloat(message.toString());
                ConnectionDB.actualizarUltimaMedicion(2, humidity, con);
                Log.logmqtt.info("Humedad actualizada: {}", humidity);
                
                break;
            case "frigorifico/door":
                float puerta= 0;
                if (message.toString().equals("Puerta abierta")) {
                    puerta = 1;
                }
                ConnectionDB.actualizarUltimaMedicion(4, puerta, con);
                Log.logmqtt.info("Puerta actualizada: {}", puerta);
                break;
            case "frigorifico/rfid":
                if(DatabaseServlet.modo.equals("INSERTAR")){
                    ConnectionDB.añadirProductoInventario(message.toString(), con);
                    Log.logmqtt.info("Producto añadido al inventario: {}", message);
                    
                }else{
                    ConnectionDB.eliminarProductoInventario(message.toString(),con);
                    Log.logmqtt.info("Producto eliminado del inventario: {}", message);
                }
                
                break;
            case "frigorifico/water":
                float agua= 0;
                if (message.toString().equals("Agua detectada")) {
                    agua = 1;
                }
                ConnectionDB.actualizarUltimaMedicion(1, agua, con);
                Log.logmqtt.info("Agua actualizada: {}", agua);
                break;
            case "frigorifico/anomalias":
                float puerta1= 0;
                float agua1= 0;
                if(message.toString().contains("Temperatura alta")){
                    float temperatura = Float.parseFloat(message.toString().replaceAll("[^0-9.]", ""));
                    ConnectionDB.insertarAnomalia(3, temperatura, con);
                    Log.logmqtt.info("Anomalia de temperatura alta: {}", temperatura);
                }else if(message.toString().contains("Humedad alta")){
                    float humedad = Float.parseFloat(message.toString().replaceAll("[^0-9.]", ""));
                    ConnectionDB.insertarAnomalia(2, humedad, con);
                    Log.logmqtt.info("Anomalia de humedad alta: {}", humedad);
                }else if (message.toString().contains("Puerta abierta")){
                    puerta1 = 1;
                    ConnectionDB.insertarAnomalia(4, puerta1, con);
                    Log.logmqtt.info("Anomalia de puerta abierta: {}", puerta1);
                }else if (message.toString().contains("Agua detectada")){
                    agua1 = 1;
                    ConnectionDB.insertarAnomalia(1, agua1, con);
                    Log.logmqtt.info("Anomalia de agua detectada: {}", agua1);
                }
            
            default:
            Log.logmqtt.error("Topic no reconocido: {}", topic);
            
            break;
        }
        if (con != null) {
            connectionDB.closeConnection(con); // Cerrar la conexión
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    public String getModo() {
        return this.modo;
    }
}
