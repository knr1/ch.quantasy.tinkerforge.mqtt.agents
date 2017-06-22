/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.quantasy.mqtt.agents;

import ch.quantasy.gateway.service.stackManager.ManagerServiceContract;
import ch.quantasy.mqtt.gateway.client.AyamlClientContract;
import ch.quantasy.mqtt.gateway.client.GatewayClient;
import ch.quantasy.tinkerforge.stack.TinkerforgeStackAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.paho.client.mqttv3.MqttException;

/**
 *
 * @author reto
 */
public class GenericAgent extends GatewayClient<AyamlClientContract> {

    private final Map<TinkerforgeStackAddress, Boolean> stacks;
    private final Set<ManagerServiceContract> managerServiceContracts;

    public GenericAgent(URI mqttURI, String clientID, AyamlClientContract contract) throws MqttException {
        super(mqttURI, clientID, contract);
        stacks = new HashMap<>();
        managerServiceContracts = new HashSet<>();
    }
    
    @Override
    public void connect() throws MqttException{
        super.connect();
        subscribe("TF/Manager/U/+/S/connection", (topic, payload) -> {
            System.out.println("Message arrived from: " + topic);
            synchronized (managerServiceContracts) {
                String managerUnit = topic.substring("TF/Manager/U/".length(), topic.indexOf("/S/connection", "TF/Manager/U/".length()));
                managerServiceContracts.add(new ManagerServiceContract(managerUnit, "Manager"));
                System.out.println(managerUnit);
                managerServiceContracts.notifyAll();
            }
        });
    }

    public ManagerServiceContract[] getManagerServiceContracts() {
        synchronized (managerServiceContracts) {
            if(managerServiceContracts.isEmpty()){
                try {
                    managerServiceContracts.wait(3000);
                } catch (InterruptedException ex) {
                    //that is ok
                }
            }
            return managerServiceContracts.toArray(new ManagerServiceContract[0]);
        }
    }

    public boolean isStackConnected(TinkerforgeStackAddress address) {
        synchronized (stacks) {
            return stacks.get(address);
        }
    }

    public void connectStacksTo(ManagerServiceContract managerServiceContract, TinkerforgeStackAddress... addresses) {

        for (TinkerforgeStackAddress address : addresses) {
            String stackName = address.getHostName() + ":" + address.getPort();
            synchronized (stacks) {
                stacks.put(address, false);
            }
            System.out.println("Subscribing to " + address);
            subscribe(managerServiceContract.STATUS_STACK_ADDRESS + "/" + stackName, (topic, payload) -> {
                System.out.println("Message arrived from: " + topic);
                Boolean isConnected = getMapper().readValue(payload, Boolean.class);
                synchronized (stacks) {
                    stacks.put(address, isConnected);
                    stacks.notifyAll();
                }
            });
            System.out.println("Connecting: " + stackName);

            publishIntent(managerServiceContract.INTENT_STACK_ADDRESS_ADD, address);
        }
        boolean allConnected = false;
        while (!allConnected) {
            allConnected = true;
            synchronized (stacks) {
                for (boolean connected : stacks.values()) {
                    allConnected &= connected;
                }
            }
            if (allConnected != true) {
                synchronized (stacks) {
                    try {
                        stacks.wait();
                    } catch (InterruptedException ex) {
                        Logger.getLogger(GenericAgent.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            } else {
                //This is an ugly hack in order to cope with a race-condition:
                //As soon as the stack is ready, it spawns new threads as soon as it detects a new Brick(let).
                //Unfortunately, it is not known, when this process is finished (@see IPConnection#enumerate)
                //Thus waiting 3 seconds might be fine.
                //This is not a solution! This states an intrinsic problem.
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ex) {
                    //That is fine.
                }
            }
        }

    }
}
