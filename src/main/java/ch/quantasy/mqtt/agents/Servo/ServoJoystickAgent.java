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
package ch.quantasy.mqtt.agents.Servo;

import ch.quantasy.gateway.message.joystick.JoystickIntent;
import ch.quantasy.gateway.message.joystick.PositionEvent;
import ch.quantasy.gateway.service.tinkerforge.joystick.JoystickServiceContract;
import ch.quantasy.gateway.service.tinkerforge.servo.ServoServiceContract;
import ch.quantasy.gateway.service.stackManager.StackManagerServiceContract;
import ch.quantasy.mqtt.agents.GenericTinkerforgeAgent;
import ch.quantasy.mqtt.agents.GenericTinkerforgeAgentContract;
import ch.quantasy.tinkerforge.device.TinkerforgeDeviceClass;
import ch.quantasy.gateway.message.stack.TinkerforgeStackAddress;
import java.net.URI;
import org.eclipse.paho.client.mqttv3.MqttException;
import ch.quantasy.gateway.message.servo.Degree;
import ch.quantasy.gateway.message.servo.PulseWidth;
import ch.quantasy.gateway.message.servo.Servo;
import ch.quantasy.gateway.message.servo.ServoIntent;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 *
 * @author reto
 */
public class ServoJoystickAgent extends GenericTinkerforgeAgent {

    private final ServoServiceContract servoServiceContract;
    private final JoystickServiceContract joystickServiceContract;

    private final Servo[] servos;

    public ServoJoystickAgent(URI mqttURI) throws MqttException, InterruptedException {
        super(mqttURI, "epnerp4", new GenericTinkerforgeAgentContract("ServoJoystick", "SJ01"));
        connect();

        servoServiceContract = new ServoServiceContract("6JLxaK", TinkerforgeDeviceClass.Servo.toString());
        joystickServiceContract = new JoystickServiceContract("hBc", TinkerforgeDeviceClass.Joystick.toString());

        servos = new Servo[2];
        servos[0] = new Servo(0);
        servos[1] = new Servo(1);

        if (super.getTinkerforgeManagerServiceContracts().length == 0) {
            System.out.println("No ManagerServcie is running... Quit.");
            return;
        }

        StackManagerServiceContract managerServiceContract = super.getTinkerforgeManagerServiceContracts()[0];
        connectTinkerforgeStacksTo(managerServiceContract, new TinkerforgeStackAddress("localhost"));
        ServoIntent servoIntent=new ServoIntent();
        servoIntent.statusLED=false;
        publishIntent(servoServiceContract.INTENT,servoIntent);
        servos[0].setPulseWidth(new PulseWidth(1000, 2000));
        servos[0].setDegree(new Degree((short) -32767, (short) 32767));
        servos[0].setPeriod(20000);
        servos[0].setEnabled(true);

        servos[1].setPulseWidth(new PulseWidth(1000, 2000));
        servos[1].setDegree(new Degree((short) -32767, (short) 32767));
        servos[1].setPeriod(20000);
        servos[1].setEnabled(true);
        Set<Servo> servoSet=new HashSet<>();
        servoSet.addAll(Arrays.asList(servos));
        servoIntent.servos=servoSet;
        servoIntent.statusLED=true;
        publishIntent(servoServiceContract.INTENT,servoIntent);
        JoystickIntent joystickIntent=new JoystickIntent();
        joystickIntent.calibrate=true;
        publishIntent(joystickServiceContract.INTENT, joystickIntent);

        subscribe(joystickServiceContract.EVENT_POSITION, (topic, payload) -> {
            SortedSet<PositionEvent> position = new TreeSet(toMessageSet(payload, PositionEvent.class));
            int joystickX = position.last().getX();
            int joystickY = position.last().getY();

            if (joystickX > 0) {
                servos[0].setPosition((short) 32767);
            }
            if (joystickX < 0) {
                servos[0].setPosition((short) -32767);
            }
            int velocityX = (int) (Math.abs((65535.0 / (100 * 100)) * (joystickX * joystickX)) + 0.5);
            if (joystickY > 0) {
                servos[1].setPosition((short) 32767);
            }
            if (joystickY < 0) {
                servos[1].setPosition((short) -32767);
            }
            int velocityY = (int) (Math.abs((65535.0 / (100 * 100)) * (joystickY * joystickY)) + 0.5);

            if (velocityX > 2) {
                servos[0].setVelocity(velocityX);
                servos[0].setEnabled(true);

            } else {
                servos[0].setVelocity(0);
                servos[0].setEnabled(false);
            }
            if (velocityY > 2) {
                servos[1].setVelocity(velocityY);
                servos[1].setEnabled(true);

            } else {
                servos[1].setVelocity(0);
                servos[1].setEnabled(false);

            }
            publishIntent(servoServiceContract.INTENT, servoIntent);
        });
        joystickIntent.calibrate=null;
        joystickIntent.positionCallbackPeriod=10L;
        publishIntent(joystickServiceContract.INTENT, joystickIntent);

    }

    public static void main(String[] args) throws Throwable {
        URI mqttURI = URI.create("tcp://localhost:1883");
        if (args.length > 0) {
            mqttURI = URI.create(args[0]);
        } else {
            System.out.printf("Per default, 'tcp://127.0.0.1:1883' is chosen.\nYou can provide another address as first argument i.e.: tcp://iot.eclipse.org:1883\n");
        }
        System.out.printf("\n%s will be used as broker address.\n", mqttURI);
        ServoJoystickAgent agent = new ServoJoystickAgent(mqttURI);
        System.in.read();
    }

}
