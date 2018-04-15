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
package ch.quantasy.mqtt.agents.motionLight;

import ch.quantasy.gateway.binding.stackManager.StackManagerServiceContract;
import ch.quantasy.gateway.binding.TimerIntent;
import ch.quantasy.gateway.binding.TimerServiceContract;
import ch.quantasy.gateway.binding.tinkerforge.motionDetectorV2.Indicator;
import ch.quantasy.gateway.binding.tinkerforge.motionDetectorV2.MotionDetectorV2Intent;
import ch.quantasy.gateway.binding.tinkerforge.motionDetectorV2.MotionDetectorV2ServiceContract;
import ch.quantasy.mqtt.agents.GenericTinkerforgeAgent;
import ch.quantasy.mqtt.agents.GenericTinkerforgeAgentContract;
import ch.quantasy.gateway.binding.stackManager.TinkerforgeStackAddress;
import java.net.URI;
import org.eclipse.paho.client.mqttv3.MqttException;

/**
 *
 * @author reto
 */
public class MotionIndicatorAgent extends GenericTinkerforgeAgent {

    private MotionDetectorV2ServiceContract motionDetectorServiceContract;

    private int detectedCounter;

    public MotionIndicatorAgent(URI mqttURI) throws MqttException {
        super(mqttURI, "3p9834h3p3", new GenericTinkerforgeAgentContract("MotionIndicator", "zzz"));
        connect();

        motionDetectorServiceContract = new MotionDetectorV2ServiceContract("DV4");

        if (super.getTinkerforgeManagerServiceContracts().length == 0) {
            System.out.println("No ManagerServcie is running... Quit.");
            return;
        }
        if (super.getTimerServiceContracts().length == 0) {
            System.out.println("No TimerServcie is running... Quit.");
            return;
        }
        TimerServiceContract timerContract = super.getTimerServiceContracts()[0];
        StackManagerServiceContract managerServiceContract = super.getTinkerforgeManagerServiceContracts()[0];
        connectTinkerforgeStacksTo(managerServiceContract, new TinkerforgeStackAddress("localhost"));
        MotionDetectorV2Intent motionDetectorIntent = new MotionDetectorV2Intent();
        motionDetectorIntent.sensitivity = 100;
        motionDetectorIntent.indicator = new Indicator(0, 0, 0);
        publishIntent(motionDetectorServiceContract.INTENT, motionDetectorIntent);

        subscribe(timerContract.EVENT_TICK + "/motionIndicator/direct", (topic, payload) -> {
            motionDetectorIntent.indicator = new Indicator(0, 0, 0);
            publishIntent(motionDetectorServiceContract.INTENT, motionDetectorIntent);
        });

        subscribe(motionDetectorServiceContract.EVENT_DETECTION_CYCLE_ENDED, (topic, payload) -> {
            System.out.println(timerContract.INTENT);
            motionDetectorIntent.indicator = new Indicator(127, 127, 127);
            publishIntent(motionDetectorServiceContract.INTENT, motionDetectorIntent);

            publishIntent(timerContract.INTENT, new TimerIntent("motionIndicator/direct", System.currentTimeMillis(), 1000 * 60, null, null, null));

        });
        subscribe(motionDetectorServiceContract.EVENT_MOTION_DETECTED, (topic, payload) -> {
            publishIntent(timerContract.INTENT, new TimerIntent("motionIndicator/direct", true));
            motionDetectorIntent.indicator = new Indicator(255, 255, 255);
            publishIntent(motionDetectorServiceContract.INTENT, motionDetectorIntent);
        });

    }

    public static void main(String[] args) throws Throwable {
        //URI mqttURI = URI.create("tcp://smarthome01:1883");
        URI mqttURI = URI.create("tcp://localhost:1883");
        if (args.length > 0) {
            mqttURI = URI.create(args[0]);
        } else {
            System.out.printf("Per default, 'tcp://127.0.0.1:1883' is chosen.\nYou can provide another address as first argument i.e.: tcp://iot.eclipse.org:1883\n");
        }
        System.out.printf("\n%s will be used as broker address.\n", mqttURI);
        MotionIndicatorAgent agent = new MotionIndicatorAgent(mqttURI);
        System.in.read();
    }

}
