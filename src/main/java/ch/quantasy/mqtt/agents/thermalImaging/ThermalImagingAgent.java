/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.quantasy.mqtt.agents.thermalImaging;

import ch.quantasy.gateway.message.stack.TinkerforgeStackAddress;
import ch.quantasy.gateway.message.stack.TinkerforgeStackIntent;
import ch.quantasy.gateway.message.thermalImage.HighContrastImageEvent;
import ch.quantasy.gateway.message.thermalImage.ImageTransferConfig;
import ch.quantasy.gateway.message.thermalImage.StatisticsEvent;
import ch.quantasy.gateway.message.thermalImage.TemperatureResolution;
import ch.quantasy.gateway.message.thermalImage.ThermalImageIntent;
import ch.quantasy.gateway.service.tinkerforge.thermalImaging.ThermalImagingServiceContract;
import ch.quantasy.gateway.service.stackManager.StackManagerServiceContract;
import ch.quantasy.mqtt.agents.GenericTinkerforgeAgent;
import ch.quantasy.mqtt.agents.GenericTinkerforgeAgentContract;
import ch.quantasy.mqtt.gateway.client.message.MessageReceiver;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import org.eclipse.paho.client.mqttv3.MqttException;

/**
 *
 * @author reto
 */
public class ThermalImagingAgent extends GenericTinkerforgeAgent {

    private StackManagerServiceContract managerServiceContract;
    private ThermalImagingServiceContract contract;
    private BufferedImage bufferedImage = null;
    private JTextArea statisticsArea = null;
    // Array for standard thermal image color palette (blue=cold, red=hot)
    private int[] paletteR = new int[256];
    private int[] paletteG = new int[256];
    private int[] paletteB = new int[256];
    // Creates standard thermal image color palette (blue=cold, red=hot)
    //This is a copy of the example provided by Tinkerforge
    private void createThermalImageColorPalette() {
        for (int x = 0; x < 256; x++) {
            paletteR[x] = (int) (255 * Math.sqrt(x / 255.0));
            paletteG[x] = (int) (255 * Math.pow(x / 255.0, 3));
            if (Math.sin(2 * Math.PI * (x / 255.0)) >= 0.0) {
                paletteB[x] = (int) (255 * Math.sin(2 * Math.PI * (x / 255.0)));
            } else {
                paletteB[x] = 0;
            }
        }
    }

    
    
    public ThermalImagingAgent(URI mqttURI) throws MqttException, InterruptedException {
        super(mqttURI, "qeriurnbgfp", new GenericTinkerforgeAgentContract("ThermalImaging", "thermalWatcher"));
        connect();
        if (super.getTinkerforgeManagerServiceContracts().length == 0) {
            System.out.println("No ManagerServcie is running... Quit.");
            return;
        }

        //---------------------------------------------------------------------
        createThermalImageColorPalette();
        JFrame jFrame = new JFrame("Thermal Image Agent");
        statisticsArea = new JTextArea() {
            @Override
            public void setSize(int width, int height) {
                super.setSize(width, height);
                this.setFont(new Font("Arial", Font.BOLD, statisticsArea.getWidth() / 50));

            }

        };
        statisticsArea.setText("Temperature-Spot-Statistics...");
        JPanel thermalPanel = new JPanel() {
            @Override
            public Dimension getMinimumSize() {
                return new Dimension(80, 60);
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (bufferedImage == null) {
                    return;
                }
                g.drawImage(resize(bufferedImage, this.getWidth(), this.getHeight()), 0, 0, null);
            }

            // Helper function for simple buffer resize
            // This is copied from the example provided by Tinkerforge
            private BufferedImage resize(BufferedImage img, int newW, int newH) {
                Image tmp = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
                BufferedImage dimg = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);

                Graphics2D g2d = dimg.createGraphics();
                g2d.drawImage(tmp, 0, 0, null);
                g2d.dispose();

                return dimg;
            }
        };
        jFrame.add(thermalPanel);
        jFrame.add(statisticsArea, BorderLayout.SOUTH);

        jFrame.setSize(800, 600);
        jFrame.setVisible(true);
        //----------------------------------------------------------------------

        managerServiceContract = super.getTinkerforgeManagerServiceContracts()[0];
        TinkerforgeStackIntent intent = new TinkerforgeStackIntent(true, new TinkerforgeStackAddress("localhost"));
        connectTinkerforgeStacksTo(managerServiceContract, new TinkerforgeStackAddress("localhost"));
        contract = new ThermalImagingServiceContract("D4p");
        subscribe(contract.EVENT_IMAGE_HIGH_CONTRAST, new MessageReceiver() {
            @Override
            public void messageReceived(String topic, byte[] mm) throws Exception {
                SortedSet<HighContrastImageEvent> images = new TreeSet<>(toMessageSet(mm, HighContrastImageEvent.class));
                if(images.size()>1)
                    System.out.println("Images: "+images.size());
                int[] image = images.last().value;

                // The following is copied from the example provided by Tinkerforge
                // Use palette mapping to create thermal image coloring 
                for (int i = 0; i < 80 * 60; i++) {
                    image[i] = (255 << 24) | (paletteR[image[i]] << 16) | (paletteG[image[i]] << 8) | (paletteB[image[i]] << 0);
                }

                // Create BufferedImage with data
                bufferedImage = new BufferedImage(80, 60, BufferedImage.TYPE_INT_ARGB);
                bufferedImage.setRGB(0, 0, 80, 60, image, 0, 80);
                thermalPanel.updateUI();
            }
        });
        subscribe(contract.EVENT_STATISTICS, new MessageReceiver() {
            @Override
            public void messageReceived(String topic, byte[] mm) throws Exception {
                SortedSet<StatisticsEvent> statistics = new TreeSet<>(toMessageSet(mm, StatisticsEvent.class));
                StatisticsEvent statistic = statistics.last();
                statisticsArea.setText("Minimum °C: " + (statistic.getSpotMeterStatistics().minimumTemperature / 100.0 - 273.15) + "\n"
                        + "Maximum °C: " + (statistic.getSpotMeterStatistics().maximumTemperature / 100.0 - 273.15) + "\n"
                        + "Mean °C: " + (statistic.getSpotMeterStatistics().meanTemperature / 100.0 - 273.15) + "\n");
            }
        });
        ThermalImageIntent thermalIntent = new ThermalImageIntent();
        thermalIntent.imageTransferConfig = ImageTransferConfig.contrast;
        thermalIntent.resolution = TemperatureResolution.from_0_to_655K;
        publishIntent(contract.INTENT, thermalIntent);

    }

    public void finish() {
        ThermalImageIntent thermalIntent = new ThermalImageIntent();
        thermalIntent.imageTransferConfig = ImageTransferConfig.none;
        publishIntent(contract.INTENT, thermalIntent);
        super.removeTinkerforgeStackFrom(managerServiceContract, new TinkerforgeStackAddress("localhost"));
        return;
    }

    public static void main(String... args) throws Throwable {
        URI mqttURI = URI.create("tcp://127.0.0.1:1883");
        //URI mqttURI = URI.create("tcp://iot.eclipse.org:1883");

        if (args.length > 0) {
            mqttURI = URI.create(args[0]);
        } else {
            System.out.printf("Per default, 'tcp://127.0.0.1:1883' is chosen.\nYou can provide another address as first argument i.e.: tcp://iot.eclipse.org:1883\n");
        }
        System.out.printf("\n%s will be used as broker address.\n", mqttURI);
        ThermalImagingAgent agent = new ThermalImagingAgent(mqttURI);
        System.in.read();
        agent.finish();
    }
}
