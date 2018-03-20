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

import ch.quantasy.gateway.binding.stackManager.StackManagerServiceContract;
import ch.quantasy.gateway.binding.tinkerforge.RGBLEDButton.ButtonState;
import ch.quantasy.gateway.binding.tinkerforge.RGBLEDButton.ButtonEvent;
import ch.quantasy.gateway.binding.tinkerforge.RGBLEDButton.RGBColor;
import ch.quantasy.gateway.binding.tinkerforge.RGBLEDButton.RGBLEDButtonIntent;
import ch.quantasy.gateway.binding.tinkerforge.RGBLEDButton.RGBLEDButtonServiceContract;
import ch.quantasy.gateway.binding.tinkerforge.stack.TinkerforgeStackAddress;
import ch.quantasy.mqtt.agents.GenericTinkerforgeAgent;
import ch.quantasy.mqtt.agents.GenericTinkerforgeAgentContract;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.paho.client.mqttv3.MqttException;

/**
 *
 * @author reto
 */
public class RGBLEDButtonAgent extends GenericTinkerforgeAgent {

    private StackManagerServiceContract managerServiceContract;
    private Thread t;
    private RGBLEDButtonServiceContract masterButton;
    private RGBLEDButtonServiceContract slaveButton;

    private Blinky blinky;

    public RGBLEDButtonAgent(URI mqttURI) throws MqttException, InterruptedException {
        super(mqttURI, "rtbtzihl", new GenericTinkerforgeAgentContract("RGBLEDButton", "flipper"));

        connect();
        if (super.getTinkerforgeManagerServiceContracts().length == 0) {
            System.out.println("No ManagerServcie is running... Quit.");
            return;
        }

        managerServiceContract = super.getTinkerforgeManagerServiceContracts()[0];
        connectTinkerforgeStacksTo(managerServiceContract, new TinkerforgeStackAddress("localhost"));
        masterButton = new RGBLEDButtonServiceContract("D3S");
        slaveButton = new RGBLEDButtonServiceContract("D3V");
        blinky = new Blinky();
        t = new Thread(blinky);
        t.start();
        {
            RGBLEDButtonIntent buttonIntent = new RGBLEDButtonIntent();
            buttonIntent.color = new RGBColor(0, 100, 255);
            publishIntent(masterButton.INTENT, buttonIntent);
            publishIntent(slaveButton.INTENT, buttonIntent);

        }
        subscribe(masterButton.EVENT_BUTTON, (topic, payload) -> {
            ButtonEvent event = toMessageSet(payload, ButtonEvent.class).last();
            RGBLEDButtonIntent buttonIntent = new RGBLEDButtonIntent();
            if (event.state == ButtonState.PRESSED) {
                blinky.setBlinkStartRelativeToNow(300000);
                buttonIntent.color = new RGBColor(100, 255, 0);
            }
            if (event.state == ButtonState.RELEASED) {
                blinky.setBlinkStartRelativeToNow(3000);
                buttonIntent.color = new RGBColor(255, 80, 0);
            }
            publishIntent(masterButton.INTENT, buttonIntent);
            publishIntent(slaveButton.INTENT, buttonIntent);
        });
        subscribe(slaveButton.EVENT_BUTTON, (topic, payload) -> {
            ButtonEvent event = toMessageSet(payload, ButtonEvent.class).last();

            if (event.state == ButtonState.PRESSED) {
                RGBLEDButtonIntent buttonIntent = new RGBLEDButtonIntent();
                buttonIntent.color = new RGBColor(255, 255, 255);
                publishIntent(masterButton.INTENT, buttonIntent);
            }
        });

    }

    class Blinky implements Runnable {

        private long blinkStart;
        private RGBLEDButtonIntent[] intents;

        public void setBlinkStartRelativeToNow(int start) {
            blinkStart = System.currentTimeMillis() + start;
        }

        @Override
        public void run() {
            intents = new RGBLEDButtonIntent[2];
            intents[0] = new RGBLEDButtonIntent(new RGBColor(0, 100, 255));
            intents[1] = new RGBLEDButtonIntent(new RGBColor(255, 0, 0));
            int flipper = 0;
            try {

                while (true) {

                    while (blinkStart > System.currentTimeMillis()) {
                        Thread.sleep(Math.max(1, System.currentTimeMillis() - blinkStart));
                    }
                    flipper++;
                    flipper %= intents.length;
                    publishIntent(masterButton.INTENT, intents[flipper]);
                    publishIntent(slaveButton.INTENT, intents[flipper]);

                    Thread.sleep(500);
                }
            } catch (InterruptedException ex) {
                Logger.getLogger(RGBLEDButtonAgent.class.getName()).log(Level.SEVERE, null, ex);
            }
            publishIntent(masterButton.INTENT, new RGBLEDButtonIntent(new RGBColor(0, 0, 0)));
            publishIntent(slaveButton.INTENT, new RGBLEDButtonIntent(new RGBColor(0, 0, 0)));

        }

    }

    public void finish() {
        t.interrupt();
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
