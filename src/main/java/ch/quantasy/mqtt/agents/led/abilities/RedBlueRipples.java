/*
 *   "TiMqWay"
 *
 *    TiMqWay(tm): A gateway to provide an MQTT-View for the Tinkerforge(tm) world (Tinkerforge-MQTT-Gateway).
 *
 *    Copyright (c) 2016 Bern University of Applied Sciences (BFH),
 *    Research Institute for Security in the Information Society (RISIS), Wireless Communications & Secure Internet of Things (WiCom & SIoT),
 *    Quellgasse 21, CH-2501 Biel, Switzerland
 *
 *    Licensed under Dual License consisting of:
 *    1. GNU Affero General Public License (AGPL) v3
 *    and
 *    2. Commercial license
 *
 *
 *    1. This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *    2. Licensees holding valid commercial licenses for TiMqWay may use this file in
 *     accordance with the commercial license agreement provided with the
 *     Software or, alternatively, in accordance with the terms contained in
 *     a written agreement between you and Bern University of Applied Sciences (BFH),
 *     Research Institute for Security in the Information Society (RISIS), Wireless Communications & Secure Internet of Things (WiCom & SIoT),
 *     Quellgasse 21, CH-2501 Biel, Switzerland.
 *
 *
 *     For further information contact <e-mail: reto.koenig@bfh.ch>
 *
 *
 */
package ch.quantasy.mqtt.agents.led.abilities;

import ch.quantasy.gateway.service.device.ledStrip.LEDStripServiceContract;
import ch.quantasy.mqtt.gateway.client.GatewayClient;
import ch.quantasy.gateway.message.ledStrip.LEDFrame;
import ch.quantasy.gateway.message.ledStrip.LEDStripDeviceConfig;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 *
 * @author reto
 */
public class RedBlueRipples extends AnLEDAbility {

    Random random = new Random();

    private final List<LEDFrame> frames;

    public RedBlueRipples(GatewayClient gatewayClient, LEDStripServiceContract ledServiceContract, LEDStripDeviceConfig config) {
        super(gatewayClient, ledServiceContract, config);
        frames = new ArrayList<>();
    }

    public void run() {
        super.setLEDFrame(getNewLEDFrame());

        LEDFrame leds = super.getNewLEDFrame();
        LEDFrame flash = super.getNewLEDFrame();

        for (int position = 0; position < flash.getNumberOfLEDs(); position++) {
            for (int channel = 0; channel < flash.getNumberOfChannels(); channel++) {
                flash.setColor(channel, position, (short) 255);
            }
        }
        super.setLEDFrame(flash);

        int RED = 255;
        int GREEN = 90;
        int BLUE = 10;

        Dot[] dots = new Dot[1];
        for (int i = 0; i < dots.length; i++) {
            dots[i] = new Dot(dots.length);
        }

        try {
            while (true) {
                while (frames.size() < 150) {
                    for (int position = 0; position < leds.getNumberOfLEDs(); position++) {
                        leds.setColor((short) 0, (short) position, (short) 0);
                        leds.setColor((short) 1, (short) position, (short) 0);
                        leds.setColor((short) 2, (short) position, (short) 0);
                        for (int i = 0; i < dots.length; i++) {
                            Color color = dots[i].getColorFor(position);
                            System.out.println(""+color);
                            dots[i].step();
                            leds.setColor((short) 0, (short) position, (short) (leds.getColor((short) 0, position) + color.getRed()));
                            leds.setColor((short) 1, (short) position, (short) (leds.getColor((short) 1, position) + color.getGreen()));
                            leds.setColor((short) 2, (short) position, (short) (leds.getColor((short) 2, position) + color.getBlue()));
                        }
                    }

                    frames.add(new LEDFrame(leds));
                }
                super.setLEDFrames(frames);
                frames.clear();

                Thread.sleep(super.getConfig().getFrameDurationInMilliseconds() * 50);

                synchronized (this) {
                    while (getCounter() > 100) {
                        this.wait(super.getConfig().getFrameDurationInMilliseconds() * 1000);
                    }
                }
            }
        } catch (InterruptedException ex) {
            super.setLEDFrame(getNewLEDFrame());
        }
    }

    static class Dot {

        private static Random random = new Random();
        private int center;
        private double strength;
        private int distance;
        private final int amountOfLEDs;
        private Color color;

        public Dot(int amountOfLEDs) {
            this.amountOfLEDs = amountOfLEDs;
            this.color=new Color(0);
            step();
        }

        public Color getColorFor(int position) {
            System.out.printf("center:%d strenght:%f distance:%d",center,strength,distance);
            if (position < center) {
                if ((position + distance) > center) {
                    return new Color((float) ((color.getRed() / 255.0) * strength), (float) ((color.getGreen() / 255.0) * strength), (float) ((color.getBlue() / 255.0) * strength));
                } else {
                    return new Color(0);
                }
            }
            if (position > center) {
                if ((position - distance) < center) {
                    return new Color((float) ((color.getRed() / 255.0) * strength), (float) ((color.getGreen() / 255.0) * strength), (float) ((color.getBlue() / 255.0) * strength));
                } else {
                    return new Color(0);
                }
            }
            return new Color((float) ((color.getRed() / 255.0) * strength), (float) ((color.getGreen() / 255.0) * strength), (float) ((color.getBlue() / 255.0) * strength));

        }

        public void step() {
            this.strength -= 0.1;
            this.distance++;
            if (color.getRGB()==-16777216) {
                strength = random.nextDouble();
                this.distance = 0;
                this.center = random.nextInt(amountOfLEDs);
                this.color = new Color(random.nextInt(255), random.nextInt(255), random.nextInt(255));
            }
        }
    }
}
