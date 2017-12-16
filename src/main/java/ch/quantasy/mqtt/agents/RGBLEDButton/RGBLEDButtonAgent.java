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
package ch.quantasy.mqtt.agents.RGBLEDButton;

import ch.quantasy.gateway.message.RGBLEDButton.ButtonState;
import ch.quantasy.gateway.message.RGBLEDButton.ButtonEvent;
import ch.quantasy.gateway.message.RGBLEDButton.RGBColor;
import ch.quantasy.gateway.message.RGBLEDButton.RGBLEDButtonIntent;
import ch.quantasy.gateway.message.stack.TinkerforgeStackAddress;
import ch.quantasy.gateway.service.device.RGBLEDButton.RGBLEDButtonServiceContract;
import ch.quantasy.gateway.service.stackManager.StackManagerServiceContract;
import ch.quantasy.mqtt.agents.GenericTinkerforgeAgent;
import ch.quantasy.mqtt.agents.GenericTinkerforgeAgentContract;
import ch.quantasy.mqtt.gateway.client.message.MessageCollector;
import java.net.URI;
import java.util.Set;
import org.eclipse.paho.client.mqttv3.MqttException;

/**
 *
 * @author reto
 */
public class RGBLEDButtonAgent extends GenericTinkerforgeAgent {

    private StackManagerServiceContract managerServiceContract;

    public RGBLEDButtonAgent(URI mqttURI) throws MqttException, InterruptedException {
        super(mqttURI, "rtbtzihl", new GenericTinkerforgeAgentContract("RGBLEDButton", "flipper"));

        connect();
        if (super.getTinkerforgeManagerServiceContracts().length == 0) {
            System.out.println("No ManagerServcie is running... Quit.");
            return;
        }

        managerServiceContract = super.getTinkerforgeManagerServiceContracts()[0];
        connectTinkerforgeStacksTo(managerServiceContract, new TinkerforgeStackAddress("localhost"));
        RGBLEDButtonServiceContract button = new RGBLEDButtonServiceContract("D46");

        MessageCollector<ButtonEvent> mc = new MessageCollector();
        {
            RGBLEDButtonIntent buttonIntent = new RGBLEDButtonIntent();
            buttonIntent.color = new RGBColor(0, 100, 255);
            publishIntent(button.INTENT, buttonIntent);
        }
        subscribe(button.EVENT_BUTTON, (topic, payload) -> {
            mc.add(topic,toMessageSet(payload, ButtonEvent.class));
            ButtonEvent event = mc.retrieveLastMessage(topic);
            RGBLEDButtonIntent buttonIntent = new RGBLEDButtonIntent();
            if (event.getState() == ButtonState.PRESSED) {
                buttonIntent.color = new RGBColor(100, 255, 0);
            }
            if (event.getState() == ButtonState.RELEASED) {
                buttonIntent.color = new RGBColor(255, 80, 0);
            }
            publishIntent(button.INTENT, buttonIntent);
        });

    }

    public void finish() {
        super.removeTinkerforgeStackFrom(managerServiceContract, new TinkerforgeStackAddress("TestBrick"));
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
        RGBLEDButtonAgent agent = new RGBLEDButtonAgent(mqttURI);
        System.in.read();
        agent.finish();
    }

}
