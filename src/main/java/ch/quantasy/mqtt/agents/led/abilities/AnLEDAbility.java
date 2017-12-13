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
import ch.quantasy.gateway.message.ledStrip.LedStripIntent;
import ch.quantasy.gateway.message.ledStrip.RenderedEvent;
import java.util.ArrayList;
import java.util.List;
import ch.quantasy.mqtt.gateway.client.message.MessageReceiver;
import java.util.SortedSet;

/**
 *
 * @author reto
 */
public abstract class AnLEDAbility implements Runnable, MessageReceiver {

    private final LEDStripServiceContract ledServiceContract;
    private final LEDStripDeviceConfig config;
    private int counter = 0;
    private final GatewayClient gatewayClient;

    public AnLEDAbility(GatewayClient gatewayClient, LEDStripServiceContract ledServiceContract, LEDStripDeviceConfig config) {
        this.ledServiceContract = ledServiceContract;
        this.config = config;
        this.gatewayClient = gatewayClient;
        gatewayClient.subscribe(ledServiceContract.EVENT_LEDs_RENDERED, this);
        LedStripIntent intent = new LedStripIntent();
        intent.config = config;
        gatewayClient.getPublishingCollector().readyToPublish(ledServiceContract.INTENT, intent);

    }

    public LEDStripServiceContract getLedServiceContract() {
        return ledServiceContract;
    }

    public LEDStripDeviceConfig getConfig() {
        return config;
    }

    public int getCounter() {
        return counter;
    }

    public GatewayClient getGatewayClient() {
        return gatewayClient;
    }

    public LEDFrame getNewLEDFrame() {

        return new LEDFrame(config.getChipType().getNumberOfChannels(), config.getNumberOfLEDs());
    }

    public void setLEDFrame(LEDFrame ledFrame) {
        LedStripIntent intent = new LedStripIntent();
        intent.LEDFrame = ledFrame;
        gatewayClient.getPublishingCollector().readyToPublish(ledServiceContract.INTENT, intent);
    }

    public void setLEDFrames(List<LEDFrame> ledFrames) {
        List<LEDFrame> frames = new ArrayList<>(ledFrames);
        LedStripIntent intent = new LedStripIntent();
        intent.LEDFrames = frames.toArray(new LEDFrame[frames.size()]);
        gatewayClient.getPublishingCollector().readyToPublish(ledServiceContract.INTENT, intent);
    }

    @Override
    public void messageReceived(String topic, byte[] payload) throws Exception {
        synchronized (this) {
            SortedSet<RenderedEvent>framesRendered = gatewayClient.toMessageSet(payload, RenderedEvent.class);
            
            if (!framesRendered.isEmpty()) {
                counter = framesRendered.last().getValue();
                this.notifyAll();
            }
        }
    }

}
