/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.quantasy.mqtt.agents.remoteSwitch;

import ch.quantasy.mqtt.gateway.client.message.AnEvent;
import ch.quantasy.mqtt.gateway.client.message.annotations.Choice;
import ch.quantasy.mqtt.gateway.client.message.annotations.NonNull;
import ch.quantasy.mqtt.gateway.client.message.annotations.Nullable;
import ch.quantasy.mqtt.gateway.client.message.annotations.Range;
import ch.quantasy.mqtt.gateway.client.message.annotations.StringForm;

/**
 *"timestamp": Math.floor((new Date).getTime() / 1000),
                "type": "switchSocketB",
                "floor": svgElement.getAttribute("floor"),
                "address": parseInt(svgElement.getAttribute("address")),
                "unit": parseInt(svgElement.getAttribute("unit")),
                "switchingValue": (svgElement.getAttribute("switchingValue") === 'true' ? "switchOn" : "switchOff").toString()
 * @author reto
 */
public class SwitcherEvent extends AnEvent {

    @NonNull
    @Choice(values = {"UG","OG","EG"})
    private String floor;
    
    @NonNull
    @Choice(values = {"switchSocketB","dimSocketB"})
    private String type;
    
    @Range(from = 0, to = 67108863)
    private long address;
    
    
    @Range(from = 0, to = 15)
    private short unit;
    
    @Nullable
    @Choice(values = {"switchOn","switchOff"})
    private String switchingValue;

    @Nullable
    @Range(from = 0, to = 15)
    private short dimValue;
    
    public long getAddress() {
        return address;
    }

    public String getSwitchingValue() {
        return switchingValue;
    }

    public short getUnit() {
        return unit;
    }
    

    public String getFloor() {
        return floor;
    }

    public String getType() {
        return type;
    }

    public short getDimValue() {
        return dimValue;
    }
    
    
}
