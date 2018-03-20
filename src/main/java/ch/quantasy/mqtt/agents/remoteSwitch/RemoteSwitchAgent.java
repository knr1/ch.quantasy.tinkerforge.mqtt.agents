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
import ch.quantasy.gateway.binding.tinkerforge.stack.TinkerforgeStackAddress;
import java.net.URI;
import org.eclipse.paho.client.mqttv3.MqttException;
import ch.quantasy.mqtt.gateway.client.message.MessageReceiver;

/**
 *
 * @author reto
 */
public class RemoteSwitchAgent extends GenericTinkerforgeAgent {

    private final RemoteSwitchServiceContract remoteSwitchUG;
    private final RemoteSwitchServiceContract remoteSwitchEG;
    private final RemoteSwitchServiceContract remoteSwitchOG;
    private final RemoteSwitchV2ServiceContract remoteSwitchListenerOG;

    private int repeats;

    public RemoteSwitchAgent(URI mqttURI) throws MqttException {
        super(mqttURI, "r4zwob2", new GenericTinkerforgeAgentContract("RemoteSwitcher", "remoteSwitcher01"));
        connect();

        remoteSwitchUG = new RemoteSwitchServiceContract("qD7");
        remoteSwitchEG = new RemoteSwitchServiceContract("jKQ");
        remoteSwitchOG = new RemoteSwitchServiceContract("jKE");
        
        remoteSwitchListenerOG=new RemoteSwitchV2ServiceContract("E1u");

        if (super.getTinkerforgeManagerServiceContracts().length == 0) {
            System.out.println("No ManagerServcie is running... Quit.");
            return;
        }

        StackManagerServiceContract managerServiceContract = super.getTinkerforgeManagerServiceContracts()[0];
        connectTinkerforgeStacksTo(managerServiceContract, new TinkerforgeStackAddress("remoteSwitchListener"),new TinkerforgeStackAddress("obergeschoss"), new TinkerforgeStackAddress("untergeschoss"), new TinkerforgeStackAddress("erdgeschoss"));

        RemoteSwitchV2Intent remoteSwitchV2Intent=new RemoteSwitchV2Intent();
        remoteSwitchV2Intent.remoteSwitchConfiguration=new RemoteSwitchConfiguration(RemoteType.B, true, 5);
        publishIntent(remoteSwitchListenerOG.INTENT, remoteSwitchV2Intent);
        subscribe(remoteSwitchListenerOG.EVENT_SWITCH_B, new MessageReceiver() {
            @Override
            public void messageReceived(String topic, byte[] mm) throws Exception {
                RemoteSwitchServiceContract contract = null;
                SwitchBEvent switcher = toMessageSet(mm, SwitchBEvent.class).last();
                if (switcher.repeats < repeats || switcher.repeats > repeats + 5) {
                    repeats = switcher.repeats;

                    if (switcher.address == 23064012 && switcher.unit == 5) {
                        contract = remoteSwitchOG;
                    }
                    if (contract == null) {
                        return;
                    }
                    RemoteSwitchIntent remoteSwitchIntent = new RemoteSwitchIntent();
                    remoteSwitchIntent.switchSocketBParameters = new SwitchSocketBParameters(4, (short) 3, SwitchSocketParameters.SwitchTo.getSwitchToFor((short) switcher.switchTo));
                    publishIntent(contract.INTENT, remoteSwitchIntent);
                }
            }
        }
        );
    }

    public static void main(String[] args) throws Throwable {
        //URI mqttURI = URI.create("tcp://smarthome01:1883");

        URI mqttURI = URI.create("tcp://127.0.0.1:1883");
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
