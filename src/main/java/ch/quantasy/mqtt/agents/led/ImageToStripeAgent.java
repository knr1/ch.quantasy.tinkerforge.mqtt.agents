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

import ch.quantasy.gateway.binding.stackManager.StackManagerServiceContract;
import ch.quantasy.mqtt.agents.led.abilities.AnLEDAbility;

import ch.quantasy.mqtt.agents.GenericTinkerforgeAgent;
import ch.quantasy.mqtt.agents.GenericTinkerforgeAgentContract;
import ch.quantasy.tinkerforge.device.TinkerforgeDeviceClass;
import ch.quantasy.gateway.binding.tinkerforge.ledStrip.LEDStripDeviceConfig;
import ch.quantasy.gateway.binding.tinkerforge.ledStrip.LEDStripServiceContract;
import ch.quantasy.gateway.binding.tinkerforge.ledStrip.LagingEvent;
import ch.quantasy.gateway.binding.tinkerforge.ledStrip.LedStripIntent;
import ch.quantasy.gateway.binding.stackManager.TinkerforgeStackAddress;
import ch.quantasy.mqtt.agents.led.abilities.DarkFire;
import ch.quantasy.mqtt.agents.led.abilities.ImageToStripe;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.paho.client.mqttv3.MqttException;

/**
 *
 * @author reto
 */
public class ImageToStripeAgent extends GenericTinkerforgeAgent {

    private final List<AnLEDAbility> abilities;
    private List<Thread> threads = new ArrayList<>();

    private final int frameDurationInMillis;
    private final int amountOfLEDs;

    public ImageToStripeAgent(URI mqttURI) throws MqttException {
        super(mqttURI, "imageToStripeAgent", new GenericTinkerforgeAgentContract("imageToStripe", "01"));
        connect();
        frameDurationInMillis = 40;
        amountOfLEDs = 11;
        abilities = new ArrayList<>();

        if (super.getTinkerforgeManagerServiceContracts().length == 0) {
            System.out.println("No ManagerServcie is running... Quit.");
            return;
        }

        StackManagerServiceContract managerServiceContract = super.getTinkerforgeManagerServiceContracts()[0];
        connectTinkerforgeStacksTo(managerServiceContract, new TinkerforgeStackAddress("localhost"));
        LedStripIntent ledIntent = new LedStripIntent();
        LEDStripDeviceConfig config = new LEDStripDeviceConfig(LEDStripDeviceConfig.ChipType.WS2812RGBW, 2000000, frameDurationInMillis, amountOfLEDs, LEDStripDeviceConfig.ChannelMapping.GBRW);
        ledIntent.config = config;
        LEDStripServiceContract ledServiceContract1 = new LEDStripServiceContract("xe9", TinkerforgeDeviceClass.LEDStrip.toString());
        publishIntent(ledServiceContract1.INTENT, ledIntent);

        //abilities.add(new DarkSparklingFire(this, ledServiceContract1, config));
        abilities.add(new ImageToStripe(this, ledServiceContract1, config));

        subscribe(ledServiceContract1.EVENT_LAGING, (topic, payload) -> {
            LagingEvent lag = toMessageSet(payload, LagingEvent.class).last();
            Logger.getLogger(ImageToStripeAgent.class.getName()).log(Level.INFO, "Laging: " + lag);
        });
        for (AnLEDAbility ability : abilities) {
            Thread t = new Thread(ability);
            t.start();
            threads.add(t);
        }

    }

    public void blackOut() {
        for (Thread thread : threads) {
            thread.interrupt();
        }
    }

    public static void main(String[] args) throws Throwable {
        URI mqttURI = URI.create("tcp://127.0.0.1:1883");
        if (args.length > 0) {
            mqttURI = URI.create(args[0]);
        } else {
            System.out.printf("Per default, 'tcp://127.0.0.1:1883' is chosen.\nYou can provide another address as first argument i.e.: tcp://iot.eclipse.org:1883\n");
        }
        System.out.printf("\n%s will be used as broker address.\n", mqttURI);
        ImageToStripeAgent agent = new ImageToStripeAgent(mqttURI);
        System.in.read();
        agent.blackOut();
    }
}
