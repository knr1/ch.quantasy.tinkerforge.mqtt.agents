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
import ch.quantasy.mqtt.agents.led.abilities.WaveAdjustableBrightness;
import ch.quantasy.mqtt.agents.GenericTinkerforgeAgent;
import ch.quantasy.mqtt.agents.GenericTinkerforgeAgentContract;
import ch.quantasy.tinkerforge.device.TinkerforgeDeviceClass;
import ch.quantasy.gateway.binding.tinkerforge.ledStrip.LEDStripDeviceConfig;
import ch.quantasy.gateway.binding.tinkerforge.ledStrip.LEDStripServiceContract;
import ch.quantasy.gateway.binding.tinkerforge.ledStrip.LedStripIntent;
import ch.quantasy.gateway.binding.tinkerforge.motionDetector.MotionDetectorServiceContract;
import ch.quantasy.gateway.binding.tinkerforge.rotaryEncoder.CountEvent;
import ch.quantasy.gateway.binding.tinkerforge.rotaryEncoder.RotaryEncoderIntent;
import ch.quantasy.gateway.binding.tinkerforge.rotaryEncoder.RotaryEncoderServiceContract;
import ch.quantasy.gateway.binding.tinkerforge.stack.TinkerforgeStackAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.paho.client.mqttv3.MqttException;
import ch.quantasy.mqtt.gateway.client.message.MessageReceiver;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 *
 * @author reto
 */
public class AmbientLEDLightAgent extends GenericTinkerforgeAgent {

    private final List<WaveAdjustableBrightness> waveList;
    private Thread timerThread;
    private final int frameDurationInMillis;
    private final int amountOfLEDs;
    private int delayInMinutes;

    public AmbientLEDLightAgent(URI mqttURI) throws MqttException {
        super(mqttURI, "349h3fdh", new GenericTinkerforgeAgentContract("AmbientLEDLight", "test01"));
        connect();
        frameDurationInMillis = 55;
        amountOfLEDs = 120;
        delayInMinutes = 1;
        waveList = new ArrayList<>();

        if (super.getTinkerforgeManagerServiceContracts().length == 0) {
            System.out.println("No ManagerServcie is running... Quit.");
            return;
        }

        StackManagerServiceContract managerServiceContract = super.getTinkerforgeManagerServiceContracts()[0];
        connectTinkerforgeStacksTo(managerServiceContract,getStackAddresses());

        RotaryEncoderServiceContract rotaryEncoderServiceContract = new RotaryEncoderServiceContract("je3", TinkerforgeDeviceClass.RotaryEncoder.toString());

        MotionDetectorServiceContract motionDetectorServiceContract = new MotionDetectorServiceContract("kfP", TinkerforgeDeviceClass.MotionDetector.toString());
        LEDStripDeviceConfig config = new LEDStripDeviceConfig(LEDStripDeviceConfig.ChipType.WS2812RGBW, 2000000, frameDurationInMillis, amountOfLEDs, LEDStripDeviceConfig.ChannelMapping.BRGW);
        subscribe(rotaryEncoderServiceContract.EVENT_COUNT, new Brightness());
        subscribe(rotaryEncoderServiceContract.EVENT_PRESSED, new MessageReceiver() {
            @Override
            public void messageReceived(String topic, byte[] mm) throws Exception {
                for (WaveAdjustableBrightness wave : waveList) {
                    if (wave.getTargetBrightness() > 0) {
                        wave.clearFrames();
                        wave.setTargetBrightness(0, 1.0);
                    } else {
                        wave.setTargetBrightness(1, 1.0);
                    }
                }
            }
        });
        RotaryEncoderIntent rotaryIntent=new RotaryEncoderIntent();
        rotaryIntent.countCallbackPeriod=100L;
        publishIntent(rotaryEncoderServiceContract.INTENT, rotaryIntent);
        LEDStripServiceContract ledServiceContract = new LEDStripServiceContract("jJE", TinkerforgeDeviceClass.LEDStrip.toString());
        LedStripIntent ledIntent=new LedStripIntent();
        ledIntent.config=config;
        publishIntent(ledServiceContract.INTENT, ledIntent);

        waveList.add(new WaveAdjustableBrightness(this, ledServiceContract, config));
        for (WaveAdjustableBrightness wave : waveList) {
            new Thread(wave).start();
        }

        subscribe(motionDetectorServiceContract.EVENT_MOTION_DETECTED, new MessageReceiver() {
            @Override
            public void messageReceived(String topic, byte[] mm) throws Exception {
                if (timerThread != null) {
                    timerThread.interrupt();
                }
                for (WaveAdjustableBrightness wave : waveList) {
                    wave.setTargetBrightness(1.0, 0.01);

                }
            }
        });
        subscribe(motionDetectorServiceContract.EVENT_DETECTION_CYCLE_ENDED, new MessageReceiver() {
            @Override
            public void messageReceived(String topic, byte[] mm) throws Exception {
                timerThread = new Thread() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(delayInMinutes * 60 * 1000);
                            for (WaveAdjustableBrightness wave : waveList) {
                                wave.setTargetBrightness(0.0, 0.001);
                            }
                        } catch (InterruptedException ex) {
                            // Interrupted while sleeping... That is ok....
                        }
                    }
                };
                timerThread.start();
            }
        });
    }

    public TinkerforgeStackAddress[] getStackAddresses() {
        return new TinkerforgeStackAddress[]{new TinkerforgeStackAddress("lights02"), new TinkerforgeStackAddress("erdgeschoss")};
    }

    public void changeAmbientBrightness(double ambientBrightness) {
        for (WaveAdjustableBrightness wave : waveList) {
            wave.changeAmbientBrightness(ambientBrightness);
        }
    }

    public class Brightness implements MessageReceiver {

        private Integer latestCount;

         @Override
        public void messageReceived(String topic, byte[] mm) throws Exception {
            SortedSet<CountEvent> countEvents= new TreeSet(toMessageSet(mm, CountEvent.class));
            if (latestCount == null) {
                latestCount = countEvents.last().value;
            }
            int difference = latestCount;
            latestCount = countEvents.last().value;
            changeAmbientBrightness((difference - latestCount) / 100.0);
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
        AmbientLEDLightAgent agent = new AmbientLEDLightAgent(mqttURI);
        System.in.read();
    }
}
