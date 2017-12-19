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
package ch.quantasy.mqtt.agents.led;

import ch.quantasy.gateway.service.device.ledStrip.LEDStripServiceContract;
import ch.quantasy.gateway.service.stackManager.StackManagerServiceContract;
import ch.quantasy.mqtt.agents.GenericTinkerforgeAgent;
import ch.quantasy.mqtt.agents.GenericTinkerforgeAgentContract;
import ch.quantasy.tinkerforge.device.TinkerforgeDeviceClass;
import ch.quantasy.gateway.message.ledStrip.LEDStripDeviceConfig;
import ch.quantasy.gateway.message.ledStrip.LagingEvent;
import ch.quantasy.gateway.message.ledStrip.LedStripIntent;
import ch.quantasy.gateway.message.stack.TinkerforgeStackAddress;
import ch.quantasy.mqtt.agents.led.abilities.AnLEDAbility;
import ch.quantasy.mqtt.agents.led.abilities.MovingDot;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.paho.client.mqttv3.MqttException;

/**
 * Diese Klasse nutzt die Möglichkeiten des Generischen TinkerforgeAgents, um
 * einen Stack anzuhängen, und eine LED-Fähigkeit als Strategie um die LEDs
 * anzusteuern. In diesem Fall ist es der Stack auf localhost, mit dem
 * LEDStripBricklet der uid... Als LED-Fähigkeit wird die MovingDot Fähigkeit
 * angehängt, welche das Management der LEDs übernimmt.
 *
 * @author reto
 */
public class LEDAgentMovingDot extends GenericTinkerforgeAgent {

    private final int frameDurationInMillis;
    private final int amountOfLEDs;

    public LEDAgentMovingDot(URI mqttURI) throws MqttException {
        super(mqttURI, "eineBeliebigeMQTT-ClientID", new GenericTinkerforgeAgentContract("LEDAgent", "movingDots"));
        connect();
        frameDurationInMillis = 30;
        amountOfLEDs = 25;

        if (super.getTinkerforgeManagerServiceContracts().length == 0) {
            System.out.println("No ManagerServcie is running... Quit.");
            return;
        }

        //We are expecting a single TinkerforgeStackManager being active... so we only take the 'first' one in order to connect the Tinkerforge Stack
        StackManagerServiceContract managerServiceContract = super.getTinkerforgeManagerServiceContracts()[0];
        connectTinkerforgeStacksTo(managerServiceContract, new TinkerforgeStackAddress("localhost"));
        LedStripIntent ledIntent = new LedStripIntent();
        LEDStripDeviceConfig config = new LEDStripDeviceConfig(LEDStripDeviceConfig.ChipType.WS2801, 2000000, frameDurationInMillis, amountOfLEDs, LEDStripDeviceConfig.ChannelMapping.GRBW);
        ledIntent.config = config;
        LEDStripServiceContract ledServiceContract = new LEDStripServiceContract("wSj", TinkerforgeDeviceClass.LEDStrip.toString());
        publishIntent(ledServiceContract.INTENT, ledIntent);

        subscribe(ledServiceContract.EVENT_LAGING, (topic, payload) -> {
            Set<LagingEvent> lag = toMessageSet(payload, LagingEvent.class);
            Logger.getLogger(LEDAgentMovingDot.class.getName()).log(Level.INFO, "Laging:", Arrays.toString(lag.toArray(new Object[0])));
        });

        new Thread(new MovingDot(this, ledServiceContract, config)).start();

    }

    public static void main(String[] args) throws Throwable {
        URI mqttURI = URI.create("tcp://127.0.0.1:1883");
        if (args.length > 0) {
            mqttURI = URI.create(args[0]);
        } else {
            System.out.printf("Per default, 'tcp://127.0.0.1:1883' is chosen.\nYou can provide another address as first argument i.e.: tcp://iot.eclipse.org:1883\n");
        }
        System.out.printf("\n%s will be used as broker address.\n", mqttURI);
        LEDAgentMovingDot agent = new LEDAgentMovingDot(mqttURI);
        System.in.read();
    }
}
