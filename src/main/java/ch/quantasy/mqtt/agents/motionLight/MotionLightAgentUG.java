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

import ch.quantasy.gateway.message.TimerIntent;
import ch.quantasy.gateway.message.remoteSwitch.RemoteSwitchIntent;
import ch.quantasy.gateway.message.remoteSwitch.SwitchSocketBParameters;
import ch.quantasy.gateway.service.tinkerforge.motionDetector.MotionDetectorServiceContract;
import ch.quantasy.gateway.service.tinkerforge.remoteSwitch.RemoteSwitchServiceContract;
import ch.quantasy.gateway.service.stackManager.StackManagerServiceContract;
import ch.quantasy.gateway.service.timer.TimerServiceContract;
import ch.quantasy.mqtt.agents.GenericTinkerforgeAgent;
import ch.quantasy.mqtt.agents.GenericTinkerforgeAgentContract;
import ch.quantasy.timer.DeviceTickerCancel;
import ch.quantasy.timer.DeviceTickerConfiguration;
import ch.quantasy.gateway.message.remoteSwitch.SwitchSocketCParameters;
import ch.quantasy.gateway.message.stack.TinkerforgeStackAddress;
import java.net.URI;
import org.eclipse.paho.client.mqttv3.MqttException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author reto
 */
public class MotionLightAgentUG extends GenericTinkerforgeAgent {

    private RemoteSwitchServiceContract remoteSwitchServiceContract;
    private MotionDetectorServiceContract motionDetectorServiceContract;

    private int detectedCounter;

    public MotionLightAgentUG(URI mqttURI) throws MqttException {
        super(mqttURI, "wrh64q3409kj", new GenericTinkerforgeAgentContract("MotionLight", "UG"));
        connect();

        remoteSwitchServiceContract = new RemoteSwitchServiceContract("qD7");
        motionDetectorServiceContract = new MotionDetectorServiceContract("kgx");

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
        connectTinkerforgeStacksTo(managerServiceContract, new TinkerforgeStackAddress("untergeschoss"));

        subscribe(timerContract.EVENT_TICK + "/lights/UG", (topic, payload) -> {
            switchLight(SwitchSocketBParameters.SwitchTo.switchOff);
        });

        subscribe(motionDetectorServiceContract.EVENT_DETECTION_CYCLE_ENDED, (topic, payload) -> {
            System.out.println(timerContract.INTENT);
            publishIntent(timerContract.INTENT, new TimerIntent("lights/UG", System.currentTimeMillis(), 1000 * 60, null, null, null));

        });
        subscribe(motionDetectorServiceContract.EVENT_MOTION_DETECTED, (topic, payload) -> {
            publishIntent(timerContract.INTENT, new TimerIntent("lights/UG", true));
            switchLight(SwitchSocketBParameters.SwitchTo.switchOn);
        });

    }
    private SwitchSocketCParameters.SwitchTo state;

    private void switchLight(SwitchSocketBParameters.SwitchTo state) {
        if (this.state == state) {
            return;
        }
        this.state = state;
        SwitchSocketBParameters config = new SwitchSocketBParameters(2, (short) 2, state);
        RemoteSwitchIntent intent = new RemoteSwitchIntent();
        intent.switchSocketBParameters = config;
        publishIntent(remoteSwitchServiceContract.INTENT, intent);
        Logger.getLogger(MotionLightAgentUG.class.getName()).log(Level.INFO, "Switching:", state);
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
        MotionLightAgentUG agent = new MotionLightAgentUG(mqttURI);
        System.in.read();
    }

}
