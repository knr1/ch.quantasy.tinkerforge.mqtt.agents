/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.quantasy.mqtt.agents.led.abilities;

import ch.quantasy.mqtt.gateway.client.GatewayClient;
import ch.quantasy.gateway.binding.tinkerforge.ledStrip.LEDFrame;
import ch.quantasy.gateway.binding.tinkerforge.ledStrip.LEDStripDeviceConfig;
import ch.quantasy.gateway.binding.tinkerforge.ledStrip.LEDStripServiceContract;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 *
 * @author reto
 */
public class Fire extends AnLEDAbility {

    Random random = new Random();

    private final List<LEDFrame> frames;

    public Fire(GatewayClient client, LEDStripServiceContract ledServiceContract, LEDStripDeviceConfig config) {
        super(client, ledServiceContract, config);
        frames = new ArrayList<>();
    }

    public void run() {
        super.setLEDFrame(getNewLEDFrame());

        LEDFrame leds = super.getNewLEDFrame();

        int RED = 255;
        int GREEN = 90;
        int BLUE = 10;
        try {
            while (true) 
            {
                while (frames.size() < 150) {
                    for (int position = 0; position < leds.getNumberOfLEDs(); position++) {
                        double damper = random.nextDouble() * 0.99;
                        leds.setColor((short) 0, (short) position, (short) Math.max(RED / 5.0, RED * damper));
                        leds.setColor((short) 1, (short) position, (short) Math.max(GREEN / 5.0,  GREEN * damper));
                        leds.setColor((short) 2, (short) position, (short) Math.max(BLUE / 5.0, BLUE * damper));
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
}
