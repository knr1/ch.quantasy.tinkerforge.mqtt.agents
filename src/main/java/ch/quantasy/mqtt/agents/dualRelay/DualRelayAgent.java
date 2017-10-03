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
package ch.quantasy.mqtt.agents.dualRelay;

import ch.quantasy.gateway.intent.dualRelay.DeviceSelectedRelayState;
import ch.quantasy.gateway.intent.dualRelay.DualRelayIntent;
import ch.quantasy.gateway.service.stackManager.StackManagerServiceContract;
import ch.quantasy.mqtt.agents.GenericTinkerforgeAgent;
import ch.quantasy.mqtt.agents.GenericTinkerforgeAgentContract;
import ch.quantasy.gateway.intent.stack.TinkerforgeStackAddress;
import java.net.URI;
import java.util.Set;
import java.util.HashSet;
import org.eclipse.paho.client.mqttv3.MqttException;
import ch.quantasy.tinkerforge.device.TinkerforgeDeviceClass;
import ch.quantasy.gateway.intent.stack.TinkerforgeStackIntent;
import ch.quantasy.gateway.service.device.dualRelay.DualRelayServiceContract;

/**
 *
 * @author reto
 */
public class DualRelayAgent extends GenericTinkerforgeAgent {

    private StackManagerServiceContract managerServiceContract;

    public DualRelayAgent(URI mqttURI) throws MqttException, InterruptedException {
        super(mqttURI, "398h3jöi", new GenericTinkerforgeAgentContract("DualRelay", "dualR"));

        connect();
        if (super.getTinkerforgeManagerServiceContracts().length == 0) {
            System.out.println("No ManagerServcie is running... Quit.");
            return;
        }

        managerServiceContract = super.getTinkerforgeManagerServiceContracts()[0];
        TinkerforgeStackIntent intent = new TinkerforgeStackIntent(true, new TinkerforgeStackAddress("localhost"));
        connectTinkerforgeStacksTo(managerServiceContract, new TinkerforgeStackAddress("localhost"));
        DualRelayServiceContract contract = new DualRelayServiceContract("bVu", TinkerforgeDeviceClass.DualRelay.toString());

        DualRelayIntent dualRelayIntent = new DualRelayIntent();
        Set<DeviceSelectedRelayState> selectedRelayStates = new HashSet<>();
        selectedRelayStates.add(new DeviceSelectedRelayState((short) 1, true));
        dualRelayIntent.selectedRelayStates = selectedRelayStates;
        publishIntent(contract.INTENT, dualRelayIntent);

    }

    public void finish() {
        super.removeTinkerforgeStackFrom(managerServiceContract, new TinkerforgeStackAddress("localhost"));
        return;
    }

    public static void main(String... args) throws Throwable {
        URI mqttURI = URI.create("tcp://127.0.0.1:1883");
        if (args.length > 0) {
            mqttURI = URI.create(args[0]);
        } else {
            System.out.printf("Per default, 'tcp://127.0.0.1:1883' is chosen.\nYou can provide another address as first argument i.e.: tcp://iot.eclipse.org:1883\n");
        }
        System.out.printf("\n%s will be used as broker address.\n", mqttURI);
        DualRelayAgent agent = new DualRelayAgent(mqttURI);
        System.in.read();
        agent.finish();
    }

}
