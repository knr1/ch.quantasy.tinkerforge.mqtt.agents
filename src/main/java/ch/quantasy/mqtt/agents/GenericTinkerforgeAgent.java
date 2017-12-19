/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.quantasy.mqtt.agents;

import ch.quantasy.gateway.message.stackManager.ConnectStatus;
import ch.quantasy.gateway.service.stackManager.StackManagerServiceContract;
import ch.quantasy.gateway.service.timer.TimerServiceContract;
import ch.quantasy.mqtt.gateway.client.contract.AyamlServiceContract;
import ch.quantasy.mqtt.gateway.client.GatewayClient;
import ch.quantasy.gateway.message.stack.TinkerforgeStackAddress;
import ch.quantasy.gateway.message.stack.TinkerforgeStackIntent;
import ch.quantasy.mqtt.gateway.client.message.Intent;
import ch.quantasy.mqtt.gateway.client.message.MessageCollector;
import ch.quantasy.mqtt.gateway.client.message.PublishingMessageCollector;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.eclipse.paho.client.mqttv3.MqttException;

/**
 *
 * @author reto
 */
public class GenericTinkerforgeAgent extends GatewayClient<AyamlServiceContract> {

    private final Map<TinkerforgeStackAddress, Boolean> stacks;
    private final Set<StackManagerServiceContract> tinkerforgeManagerServiceContracts;
    private final Map<StackManagerServiceContract, Set<TinkerforgeStackAddress>> managedStacks;
    private final Set<TimerServiceContract> timerServiceContracts;

    private final MessageCollector intentCollector;
    private final PublishingMessageCollector intentPublisher;

    public GenericTinkerforgeAgent(URI mqttURI, String clientID, AyamlServiceContract contract) throws MqttException {
        super(mqttURI, clientID, contract);
        intentCollector = new MessageCollector();
        intentPublisher = new PublishingMessageCollector(intentCollector, this);
        stacks = new HashMap<>();
        tinkerforgeManagerServiceContracts = new HashSet<>();
        managedStacks = new HashMap<>();
        timerServiceContracts = new HashSet<>();
    }

    public void publishIntent(String topic, Intent intent) {
        intentPublisher.readyToPublish(topic, intent);
    }

    public void collectIntent(String topic, Intent intent) {
        intentCollector.add(topic, intent);
    }

    public void publishIntent(String topic) {
        intentPublisher.readyToPublish(topic);
    }

    @Override
    public void connect() throws MqttException {
        super.connect();
        subscribe("TF/Manager/U/+/S/connection", (topic, payload) -> {
            synchronized (tinkerforgeManagerServiceContracts) {
                String managerUnit = topic.split("/")[3];
                tinkerforgeManagerServiceContracts.add(new StackManagerServiceContract(managerUnit));
                System.out.println(managerUnit);
                tinkerforgeManagerServiceContracts.notifyAll();
            }
        });
        subscribe("TF/Manager/U/+/S/stack/address/#", (topic, payload) -> {
            synchronized (managedStacks) {
                System.out.println("--->" + topic);
                String managedStackAddressParts[] = topic.split("/");
                StackManagerServiceContract managerServiceContract = new StackManagerServiceContract(managedStackAddressParts[3]);
                Set<TinkerforgeStackAddress> addresses = managedStacks.get(managerServiceContract);
                if (addresses == null) {
                    addresses = new HashSet<>();
                    managedStacks.put(managerServiceContract, addresses);
                }
                String[] stackAddressParts = managedStackAddressParts[7].split(":");
                System.out.println(Arrays.toString(stackAddressParts));
                if (payload != null) {
                    MessageCollector<ConnectStatus> collector = new MessageCollector<>();
                    collector.add(topic,toMessageSet(payload, ConnectStatus.class));
                    ConnectStatus addressStatus = collector.retrieveFirstMessage(topic);
                    if (addressStatus.value) {
                        addresses.add(new TinkerforgeStackAddress(stackAddressParts[0], Integer.parseInt(stackAddressParts[1])));
                        System.out.println(stackAddressParts[0] + " available.");
                        managedStacks.notifyAll();
                        return;
                    }
                }
                managedStacks.remove(new TinkerforgeStackAddress(stackAddressParts[0], Integer.parseInt(stackAddressParts[1])));
                System.out.println(stackAddressParts[0] + " gone.");
            }
        });
        subscribe("Timer/Tick/U/+/S/connection", (topic, payload) -> {
            synchronized (timerServiceContracts) {
                String timerUnit = topic.split("/")[3];
                timerServiceContracts.add(new TimerServiceContract(timerUnit));
                System.out.println(timerUnit);
                timerServiceContracts.notifyAll();
            }
        });
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ex) {
            //Well this is ok
        }
    }

    public StackManagerServiceContract[] getTinkerforgeManagerServiceContracts() {
        synchronized (tinkerforgeManagerServiceContracts) {
            if (tinkerforgeManagerServiceContracts.isEmpty()) {
                try {
                    tinkerforgeManagerServiceContracts.wait(3000);
                } catch (InterruptedException ex) {
                    //that is ok
                }
            }
            return tinkerforgeManagerServiceContracts.toArray(new StackManagerServiceContract[0]);
        }
    }

    public TimerServiceContract[] getTimerServiceContracts() {
        synchronized (timerServiceContracts) {
            if (timerServiceContracts.isEmpty()) {
                try {
                    timerServiceContracts.wait(3000);
                } catch (InterruptedException ex) {
                    //that is ok
                }
            }
            return timerServiceContracts.toArray(new TimerServiceContract[0]);
        }
    }

    public Set<TinkerforgeStackAddress> getManagedTinkerforgeStacks(StackManagerServiceContract managerServiceContract) {
        Set<TinkerforgeStackAddress> addresses = new HashSet<>();
        Set stackSet = this.managedStacks.get(managerServiceContract);
        if (stackSet != null) {
            addresses.addAll(stackSet);
        }
        return addresses;
    }

    public void removeTinkerforgeStackFrom(StackManagerServiceContract managerServiceContract, TinkerforgeStackAddress address) {
        publishIntent(managerServiceContract.INTENT, new TinkerforgeStackIntent(false, address));
    }

    public void connectTinkerforgeStacksTo(StackManagerServiceContract managerServiceContract, TinkerforgeStackAddress... addresses) {
        for (TinkerforgeStackAddress address : addresses) {
            connectTinkerforgeStackTo(managerServiceContract, address);
        }
    }

    public void connectTinkerforgeStackTo(StackManagerServiceContract managerServiceContract, TinkerforgeStackAddress address) {
        if (!address.getHostName().equals("localhost")) {
            for (Set<TinkerforgeStackAddress> managedStacks : managedStacks.values()) {
                if (managedStacks.contains(address)) {
                    return;
                }
            }
        }
        String stackName = address.getHostName() + ":" + address.getPort();
        synchronized (stacks) {
            stacks.put(address, false);
        }
        System.out.println("Subscribing to " + address);
        subscribe(managerServiceContract.STATUS_STACK_ADDRESS + "/" + stackName, (topic, payload) -> {
            Boolean isConnected = false;
            MessageCollector<ConnectStatus> collector = new MessageCollector();
            collector.add(topic, toMessageSet(payload, ConnectStatus.class));
            isConnected = collector.retrieveFirstMessage(topic).value;
            synchronized (stacks) {
                stacks.put(address, isConnected);
                stacks.notifyAll();
            }
        });
        System.out.println("Connecting: " + stackName);

        publishIntent(managerServiceContract.INTENT, new TinkerforgeStackIntent(true, address));

        synchronized (stacks) {
            while (!stacks.get(address)) {
                try {
                    stacks.wait(500);
                } catch (InterruptedException ex) {
                    //That is ok 
                }
            }
        }
        System.out.println("Connected: " + stackName);

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
