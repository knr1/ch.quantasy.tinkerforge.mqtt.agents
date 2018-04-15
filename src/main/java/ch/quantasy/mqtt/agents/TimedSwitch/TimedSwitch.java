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
package ch.quantasy.mqtt.agents.TimedSwitch;

import ch.quantasy.gateway.binding.stackManager.StackManagerServiceContract;
import ch.quantasy.gateway.binding.EpochDeltaEvent;
import ch.quantasy.gateway.binding.TimerIntent;
import ch.quantasy.gateway.binding.TimerServiceContract;
import ch.quantasy.gateway.binding.tinkerforge.remoteSwitch.RemoteSwitchIntent;
import ch.quantasy.gateway.binding.tinkerforge.remoteSwitch.RemoteSwitchServiceContract;
import ch.quantasy.mqtt.agents.GenericTinkerforgeAgent;
import ch.quantasy.mqtt.agents.GenericTinkerforgeAgentContract;
import ch.quantasy.gateway.binding.tinkerforge.remoteSwitch.SwitchSocketCParameters;
import java.net.URI;
import org.eclipse.paho.client.mqttv3.MqttException;
import ch.quantasy.gateway.binding.stackManager.TinkerforgeStackAddress;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 *
 * @author reto
 */
public class TimedSwitch extends GenericTinkerforgeAgent {

    private final RemoteSwitchServiceContract remoteSwitchServiceContract;

    public TimedSwitch(URI mqttURI) throws MqttException {
        super(mqttURI, "9520efj30sdk", new GenericTinkerforgeAgentContract("TimedSwitch", "qD7"));
        connect();
        remoteSwitchServiceContract = new RemoteSwitchServiceContract("qD7");

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
        subscribe(timerContract.EVENT_TICK + "/poolPump", (topic, payload) -> {
            SortedSet<EpochDeltaEvent> epochTimeInMillis = new TreeSet(toMessageSet(payload, EpochDeltaEvent.class));
            LocalDateTime theDate
                    = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochTimeInMillis.first().getTimeStamp()), ZoneId.systemDefault());
            int hour = theDate.getHour();
            System.out.printf("Time: %s -> Switch: ", theDate);
            if (state == SwitchSocketCParameters.SwitchTo.switchOn || (hour > 21 || hour < 7)) {
                switchMotor(SwitchSocketCParameters.SwitchTo.switchOff);
                state = SwitchSocketCParameters.SwitchTo.switchOff;
                System.out.println("off");
            } else {
                switchMotor(SwitchSocketCParameters.SwitchTo.switchOn);
                state = SwitchSocketCParameters.SwitchTo.switchOn;
                System.out.println("on");
            }
        });

        subscribe(timerContract.EVENT_TICK + "/garden", (topic, payload) -> {
            switchAlwaysOn();
        });
        publishIntent(timerContract.INTENT, new TimerIntent("poolPump", null, 0, 1000 * 60 * 60, null, null));
        publishIntent(timerContract.INTENT, new TimerIntent("garden", null, 0, 1000 * 60 * 30, null, null));

    }

    private SwitchSocketCParameters.SwitchTo state;

    private void switchMotor(SwitchSocketCParameters.SwitchTo state) {
        RemoteSwitchIntent intent = new RemoteSwitchIntent();
        if (this.state == state) {
            return;
        }
        this.state = state;
        intent.switchSocketCParameters = new SwitchSocketCParameters('L', (short) 2, state);

        publishIntent(remoteSwitchServiceContract.INTENT, intent);
        System.out.println("Switching: " + state);
    }

    private void switchAlwaysOn() {
        RemoteSwitchIntent intent = new RemoteSwitchIntent();
        intent.switchSocketCParameters = new SwitchSocketCParameters('L', (short) 1, SwitchSocketCParameters.SwitchTo.switchOn);
        publishIntent(remoteSwitchServiceContract.INTENT, intent);
    }

    public static void main(String[] args) throws Throwable {
        URI mqttURI = URI.create("tcp://localhost:1883");

        if (args.length > 0) {
            mqttURI = URI.create(args[0]);
        } else {
            System.out.printf("Per default, 'tcp://127.0.0.1:1883' is chosen.\nYou can provide another address as first argument i.e.: tcp://iot.eclipse.org:1883\n");
        }
        System.out.printf("\n%s will be used as broker address.\n", mqttURI);
        TimedSwitch agent = new TimedSwitch(mqttURI);
        System.in.read();
    }

}
