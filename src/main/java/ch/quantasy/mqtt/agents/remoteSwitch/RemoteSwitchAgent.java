/*
 * /*
 *  *   "TiMqWay"
 *  *
 *  *    TiMqWay(tm): A gateway to provide an MQTT-View for the Tinkerforge(tm) world (Tinkerforge-MQTT-Gateway).
 *  *
 *  *    Copyright (c) 2016 Bern University of Applied Sciences (BFH),
 *  *    Research Institute for Security in the Information Society (RISIS), Wireless Communications & Secure Internet of Things (WiCom & SIoT),
 *  *    Quellgasse 21, CH-2501 Biel, Switzerland
 *  *
 *  *    Licensed under Dual License consisting of:
 *  *    1. GNU Affero General Public License (AGPL) v3
 *  *    and
 *  *    2. Commercial license
 *  *
 *  *
 *  *    1. This program is free software: you can redistribute it and/or modify
 *  *     it under the terms of the GNU Affero General Public License as published by
 *  *     the Free Software Foundation, either version 3 of the License, or
 *  *     (at your option) any later version.
 *  *
 *  *     This program is distributed in the hope that it will be useful,
 *  *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  *     GNU Affero General Public License for more details.
 *  *
 *  *     You should have received a copy of the GNU Affero General Public License
 *  *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  *
 *  *
 *  *    2. Licensees holding valid commercial licenses for TiMqWay may use this file in
 *  *     accordance with the commercial license agreement provided with the
 *  *     Software or, alternatively, in accordance with the terms contained in
 *  *     a written agreement between you and Bern University of Applied Sciences (BFH),
 *  *     Research Institute for Security in the Information Society (RISIS), Wireless Communications & Secure Internet of Things (WiCom & SIoT),
 *  *     Quellgasse 21, CH-2501 Biel, Switzerland.
 *  *
 *  *
 *  *     For further information contact <e-mail: reto.koenig@bfh.ch>
 *  *
 *  *
 */
package ch.quantasy.mqtt.agents.remoteSwitch;

import ch.quantasy.gateway.binding.stackManager.StackManagerServiceContract;
import ch.quantasy.mqtt.agents.GenericTinkerforgeAgent;
import ch.quantasy.mqtt.agents.GenericTinkerforgeAgentContract;
import ch.quantasy.gateway.binding.tinkerforge.remoteSwitch.RemoteSwitchIntent;
import ch.quantasy.gateway.binding.tinkerforge.remoteSwitch.RemoteSwitchServiceContract;
import ch.quantasy.gateway.binding.tinkerforge.remoteSwitch.SwitchSocketBParameters;
import ch.quantasy.gateway.binding.tinkerforge.remoteSwitch.SwitchSocketParameters;
import ch.quantasy.gateway.binding.tinkerforge.remoteSwitchV2.RemoteSwitchConfiguration;
import ch.quantasy.gateway.binding.tinkerforge.remoteSwitchV2.RemoteSwitchV2Intent;
import ch.quantasy.gateway.binding.tinkerforge.remoteSwitchV2.RemoteSwitchV2ServiceContract;
import ch.quantasy.gateway.binding.tinkerforge.remoteSwitchV2.RemoteType;
import ch.quantasy.gateway.binding.tinkerforge.remoteSwitchV2.SwitchBEvent;
import ch.quantasy.gateway.binding.stackManager.TinkerforgeStackAddress;
import ch.quantasy.mqtt.gateway.client.message.Message;
import java.net.URI;
import org.eclipse.paho.client.mqttv3.MqttException;
import ch.quantasy.mqtt.gateway.client.message.MessageReceiver;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author reto
 */
public class RemoteSwitchAgent extends GenericTinkerforgeAgent {

    private final RemoteSwitchServiceContract remoteSwitchUG;
    private final RemoteSwitchServiceContract remoteSwitchEG;
    private final RemoteSwitchServiceContract remoteSwitchOG;
    private final RemoteSwitchV2ServiceContract remoteSwitchListenerBEG;
    private final RemoteSwitchV2ServiceContract remoteSwitchListenerAOG;
    private final RemoteSwitchV2ServiceContract remoteSwitchListenerBOG;
    private final MessageReceiver switcherB;

    public RemoteSwitchAgent(URI mqttURI) throws MqttException {
        super(mqttURI, "r4gfkjgzwob2", new GenericTinkerforgeAgentContract("RemoteSwitcher", "remoteSwitcher01"));
        connect();
        switcherB = new SwitcherB();
        remoteSwitchUG = new RemoteSwitchServiceContract("qD7");
        remoteSwitchEG = new RemoteSwitchServiceContract("jKQ");
        remoteSwitchOG = new RemoteSwitchServiceContract("jKE");

        remoteSwitchListenerBOG = new RemoteSwitchV2ServiceContract("E1u");
        remoteSwitchListenerBEG = new RemoteSwitchV2ServiceContract("E1y");
        remoteSwitchListenerAOG = new RemoteSwitchV2ServiceContract("E1r");

        if (super.getTinkerforgeManagerServiceContracts().length == 0) {
            System.out.println("No ManagerServcie is running... Quit.");
            return;
        }

        StackManagerServiceContract managerServiceContract = super.getTinkerforgeManagerServiceContracts()[0];
        connectTinkerforgeStacksTo(managerServiceContract, new TinkerforgeStackAddress("remoteSwitchListener"), new TinkerforgeStackAddress("obergeschoss"), new TinkerforgeStackAddress("untergeschoss"), new TinkerforgeStackAddress("erdgeschoss"));

        RemoteSwitchV2Intent remoteSwitchV2Intent = new RemoteSwitchV2Intent();
        remoteSwitchV2Intent.remoteSwitchConfiguration = new RemoteSwitchConfiguration(RemoteType.B, true, 2);
        publishIntent(remoteSwitchListenerBOG.INTENT, remoteSwitchV2Intent);
        subscribe(remoteSwitchListenerBOG.EVENT_SWITCH_B, switcherB);

        publishIntent(remoteSwitchListenerBEG.INTENT, remoteSwitchV2Intent);
        subscribe(remoteSwitchListenerBEG.EVENT_SWITCH_B, switcherB);

        //remoteSwitchV2Intent.remoteSwitchConfiguration = new RemoteSwitchConfiguration(RemoteType.A, true, 2);
        //publishIntent(remoteSwitchListenerAOG.INTENT, remoteSwitchV2Intent);
        //subscribe(remoteSwitchListenerAOG.EVENT_SWITCH_A, switcherB);
    }

    class SwitcherB implements MessageReceiver {

        private Map<String, ManagedRepeats> repeatsMap = new HashMap<>();

        @Override
        public void messageReceived(String topic, byte[] mm) throws Exception {
            RemoteSwitchServiceContract contract = null;
            SwitchBEvent lastSwitchingEvent = toMessageSet(mm, SwitchBEvent.class).last();
            synchronized (this) {
                ManagedRepeats managedRepeats = repeatsMap.get("" + lastSwitchingEvent.address);
                if (managedRepeats == null) {
                    managedRepeats = new ManagedRepeats();
                    managedRepeats.manager = topic;
                    repeatsMap.put("" + lastSwitchingEvent.address, managedRepeats);
                } else if (lastSwitchingEvent.getTimeStamp() > managedRepeats.timeStamp + 2000000000L) {
                    managedRepeats.manager = topic;
                }
                if (!managedRepeats.manager.equals(topic)) {
                    return;
                }

                if (lastSwitchingEvent.repeats < managedRepeats.repeats || lastSwitchingEvent.repeats > managedRepeats.repeats + 5) {
                    managedRepeats.repeats = lastSwitchingEvent.repeats;
                    managedRepeats.timeStamp = lastSwitchingEvent.getTimeStamp();

                    if (lastSwitchingEvent.address == 23064012 && lastSwitchingEvent.unit == 5) {
                        contract = remoteSwitchOG;
                        RemoteSwitchIntent remoteSwitchIntent = new RemoteSwitchIntent();
                        //Dusche
                        remoteSwitchIntent.switchSocketBParameters = new SwitchSocketBParameters(4, (short) 3, SwitchSocketParameters.SwitchTo.getSwitchToFor((short) lastSwitchingEvent.switchTo));
                        publishIntent(contract.INTENT, remoteSwitchIntent);
                    }
                    if (lastSwitchingEvent.address == 23064012 && lastSwitchingEvent.unit == 13) {
                        contract = remoteSwitchOG;
                        RemoteSwitchIntent remoteSwitchIntent = new RemoteSwitchIntent();
                        //OG-Gallerie
                        remoteSwitchIntent.switchSocketBParameters = new SwitchSocketBParameters(4, (short) 0, SwitchSocketParameters.SwitchTo.getSwitchToFor((short) lastSwitchingEvent.switchTo));
                        publishIntent(contract.INTENT, remoteSwitchIntent);
                    }
                    if (lastSwitchingEvent.address == 26571724 && lastSwitchingEvent.unit == 5) {
                        contract = remoteSwitchUG;
                        RemoteSwitchIntent remoteSwitchIntent = new RemoteSwitchIntent();
                        //UG-Sport
                        remoteSwitchIntent.switchSocketBParameters = new SwitchSocketBParameters(2, (short) 0, SwitchSocketParameters.SwitchTo.getSwitchToFor((short) lastSwitchingEvent.switchTo));
                        publishIntent(contract.INTENT, remoteSwitchIntent);
                    }
                    if (lastSwitchingEvent.address == 26571724 && lastSwitchingEvent.unit == 13) {
                        contract = remoteSwitchUG;
                        RemoteSwitchIntent remoteSwitchIntent = new RemoteSwitchIntent();
                        //UG-Musik
                        remoteSwitchIntent.switchSocketBParameters = new SwitchSocketBParameters(2, (short) 1, SwitchSocketParameters.SwitchTo.getSwitchToFor((short) lastSwitchingEvent.switchTo));
                        publishIntent(contract.INTENT, remoteSwitchIntent);
                    }
                    //Ist noch direkt geschaltet
//                    if (switcher.address == 33202008 && switcher.unit == 5) {
//                        contract = remoteSwitchUG;
//                        RemoteSwitchIntent remoteSwitchIntent = new RemoteSwitchIntent();
//                        //UG-Gang
//                        remoteSwitchIntent.switchSocketBParameters = new SwitchSocketBParameters(2, (short) 2, SwitchSocketParameters.SwitchTo.getSwitchToFor((short) switcher.switchTo));
//                        publishIntent(contract.INTENT, remoteSwitchIntent);
//                    }
                    if (lastSwitchingEvent.address == 33202008 && lastSwitchingEvent.unit == 13) {
                        contract = remoteSwitchOG;
                        RemoteSwitchIntent remoteSwitchIntent = new RemoteSwitchIntent();
                        //OG-Gallerie
                        remoteSwitchIntent.switchSocketBParameters = new SwitchSocketBParameters(4, (short) 0, SwitchSocketParameters.SwitchTo.getSwitchToFor((short) lastSwitchingEvent.switchTo));
                        publishIntent(contract.INTENT, remoteSwitchIntent);
                    }
                }
            }

        }

        class ManagedRepeats {

            public int repeats;
            public String manager;
            public long timeStamp;
        }
    }

    public static void main(String[] args) throws Throwable {
        URI mqttURI = URI.create("tcp://smarthome01:1883");

        //URI mqttURI = URI.create("tcp://127.0.0.1:1883");
        if (args.length > 0) {
            mqttURI = URI.create(args[0]);
        } else {
            System.out.printf("Per default, 'tcp://127.0.0.1:1883' is chosen.\nYou can provide another address as first argument i.e.: tcp://iot.eclipse.org:1883\n");
        }
        System.out.printf("\n%s will be used as broker address.\n", mqttURI);
        RemoteSwitchAgent agent = new RemoteSwitchAgent(mqttURI);
        System.in.read();
    }

}
