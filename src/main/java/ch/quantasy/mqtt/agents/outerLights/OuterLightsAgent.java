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
package ch.quantasy.mqtt.agents.outerLights;

import ch.quantasy.gateway.message.ambientLight.AmbientLightIntent;
import ch.quantasy.gateway.service.device.ambientLight.AmbientLightServiceContract;
import ch.quantasy.gateway.service.device.dc.DCServiceContract;
import ch.quantasy.gateway.service.device.motionDetector.MotionDetectorServiceContract;
import ch.quantasy.gateway.service.stackManager.StackManagerServiceContract;
import ch.quantasy.gateway.service.timer.TimerServiceContract;
import ch.quantasy.mqtt.agents.GenericTinkerforgeAgent;
import ch.quantasy.mqtt.agents.GenericTinkerforgeAgentContract;
import ch.quantasy.gateway.message.stack.TinkerforgeStackAddress;
import java.net.URI;
import org.eclipse.paho.client.mqttv3.MqttException;
import ch.quantasy.mqtt.gateway.client.message.MessageReceiver;
import ch.quantasy.gateway.message.ambientLight.DeviceIlluminanceCallbackThreshold;
import ch.quantasy.gateway.message.ambientLight.IlluminanceEvent;
import ch.quantasy.gateway.message.dc.DCIntent;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author reto
 */
public class OuterLightsAgent extends GenericTinkerforgeAgent {

    private final DCServiceContract dcServiceContract;
    private List<MotionDetectorServiceContract> motionDetectorServiceContracts;
    private List<AmbientLightServiceContract> ambientLightServiceContracts;
    private final Thread t;
    private final DelayedOff delayedOff;
    private int powerInPercent;

    public OuterLightsAgent(URI mqttURI) throws MqttException {
        super(mqttURI, "wrth563g", new GenericTinkerforgeAgentContract("OuterLights", "01"));
        connect();
        powerInPercent = 100;
        delayedOff = new DelayedOff();
        t = new Thread(delayedOff);
        t.start();
        motionDetectorServiceContracts = new ArrayList<>();
        ambientLightServiceContracts = new ArrayList<>();
        motionDetectorServiceContracts.add(new MotionDetectorServiceContract("kgB"));
        motionDetectorServiceContracts.add(new MotionDetectorServiceContract("kfP"));
        ambientLightServiceContracts.add(new AmbientLightServiceContract("jxr"));
        dcServiceContract = new DCServiceContract("6kP5Zh");

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
        connectTinkerforgeStacksTo(managerServiceContract, new TinkerforgeStackAddress("erdgeschoss"));

        DCIntent dcIntent = new DCIntent();
        dcIntent.acceleration = 10000;
        dcIntent.driveMode = 1;
        dcIntent.pwmFrequency = 20000;
        dcIntent.enable = true;
        publishIntent(dcServiceContract.INTENT, dcIntent);

        for (MotionDetectorServiceContract motionDetectorServiceContract : motionDetectorServiceContracts) {
            subscribe(motionDetectorServiceContract.EVENT_MOTION_DETECTED, (String topic, byte[] mm) -> {
                delayedOff.delayUntil(System.currentTimeMillis() + (1000 * 60 * 60));
            });
            subscribe(motionDetectorServiceContract.EVENT_DETECTION_CYCLE_ENDED, (String topic, byte[] mm) -> {
                delayedOff.delayUntil(System.currentTimeMillis() + 20000);
            });
        }
        for (AmbientLightServiceContract ambientLightServiceContract : ambientLightServiceContracts) {
            AmbientLightIntent ambientIntent = new AmbientLightIntent();
            ambientIntent.debouncePeriod = 5000L;
            ambientIntent.illuminanceThreshold = new DeviceIlluminanceCallbackThreshold('o', 20, 100);
            publishIntent(ambientLightServiceContract.INTENT, ambientIntent);
            subscribe(ambientLightServiceContract.EVENT_ILLUMINANCE_REACHED, (String topic, byte[] payload) -> {
                SortedSet<IlluminanceEvent> illuminances = toMessageSet(payload, IlluminanceEvent.class);
                IlluminanceEvent illuminance = illuminances.last();
                if (illuminance.getValue() < 100) {
                    delayedOff.setPaused(false);
                } else if (illuminance.getValue() > 20) {
                    delayedOff.setPaused(true);
                }
                Logger.getLogger(OuterLightsAgent.class.getName()).log(Level.INFO, "Illuminance: ", illuminance);
            });
        }
    }

    class DelayedOff implements Runnable {

        private long delayUntil;
        private int currentPowerInPercent;
        private boolean isPaused;

        public synchronized void delayUntil(long timeInFuture) {
            if (timeInFuture < System.currentTimeMillis()) {
                return;
            }
            this.delayUntil = timeInFuture;
            this.notifyAll();
        }

        public synchronized void setPaused(boolean isPaused) {
            this.isPaused = isPaused;
            this.notifyAll();
        }

        @Override
        public void run() {
            while (true) {
                synchronized (this) {
                    while (delayUntil < System.currentTimeMillis() || isPaused) {
                        try {
                            this.wait(10000);
                        } catch (InterruptedException ex) {
                        }
                    }
                    DCIntent dcIntent = new DCIntent();
                    dcIntent.velocity = (short) ((32767 / 100) * powerInPercent);
                    publishIntent(dcServiceContract.INTENT, dcIntent);
                    while (delayUntil > System.currentTimeMillis()) {
                        if (currentPowerInPercent != powerInPercent) {
                            currentPowerInPercent = powerInPercent;
                            dcIntent.velocity = (short) ((32767 / 100) * currentPowerInPercent);
                            publishIntent(dcServiceContract.INTENT, dcIntent);
                        }
                        long delay = delayUntil - System.currentTimeMillis();
                        try {
                            this.wait(delay);
                        } catch (InterruptedException ex) {
                        }
                    }
                    dcIntent.velocity = (short) ((32767 / 100) * 0);
                    publishIntent(dcServiceContract.INTENT, dcIntent);
                }
            }
        }

    }

    public static void main(String[] args) throws Throwable {
        URI mqttURI = URI.create("tcp://localhost:1883");
        if (args.length > 0) {
            mqttURI = URI.create(args[0]);
        } else {
            System.out.printf("Per default, 'tcp://127.0.0.1:1883' is chosen.\nYou can provide another address as first argument i.e.: tcp://iot.eclipse.org:1883\n");
        }
        System.out.printf("\n%s will be used as broker address.\n", mqttURI);
        OuterLightsAgent agent = new OuterLightsAgent(mqttURI);
        System.in.read();
    }

}
