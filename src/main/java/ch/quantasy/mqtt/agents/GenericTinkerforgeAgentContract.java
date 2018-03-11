/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.quantasy.mqtt.agents;

import ch.quantasy.mqtt.gateway.client.contract.AyamlServiceContract;
import ch.quantasy.mqtt.gateway.client.message.Message;
import java.util.Map;

/**
 *
 * @author reto
 */
public class GenericTinkerforgeAgentContract extends AyamlServiceContract{

    public GenericTinkerforgeAgentContract(String rootContext, String baseClass) {
        super("Agent",rootContext, baseClass);
    }

    @Override
    public void setMessageTopics(Map<String, Class<? extends Message>> messageTopicMap) {
        //Nothing
    }
    
    
}
