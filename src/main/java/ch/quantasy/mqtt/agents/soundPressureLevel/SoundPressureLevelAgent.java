/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.quantasy.mqtt.agents.soundPressureLevel;

import ch.quantasy.gateway.binding.stackManager.StackManagerServiceContract;
import ch.quantasy.gateway.binding.stackManager.TinkerforgeStackAddress;
import ch.quantasy.gateway.binding.stackManager.TinkerforgeStackIntent;
import ch.quantasy.gateway.binding.tinkerforge.soundPressureLevel.Configuration;
import ch.quantasy.gateway.binding.tinkerforge.soundPressureLevel.FFT;
import ch.quantasy.gateway.binding.tinkerforge.soundPressureLevel.SoundPressureLevelIntent;
import ch.quantasy.gateway.binding.tinkerforge.soundPressureLevel.SoundPressureLevelServiceContract;
import ch.quantasy.gateway.binding.tinkerforge.soundPressureLevel.SpectrumEvent;
import ch.quantasy.gateway.binding.tinkerforge.soundPressureLevel.Weighting;
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
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import org.eclipse.paho.client.mqttv3.MqttException;

/**
 *
 * @author reto
 */
public class SoundPressureLevelAgent extends GenericTinkerforgeAgent {

    private StackManagerServiceContract managerServiceContract;
    private SoundPressureLevelServiceContract contract;
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

    public SoundPressureLevelAgent(URI mqttURI) throws MqttException, InterruptedException {
        super(mqttURI, "345g567j", new GenericTinkerforgeAgentContract("SoundPressure", "SoundWatcher"));
        connect();
        if (super.getTinkerforgeManagerServiceContracts().length == 0) {
            System.out.println("No ManagerServcie is running... Quit.");
            return;
        }

        //---------------------------------------------------------------------
        createThermalImageColorPalette();
        JFrame jFrame = new JFrame("Sound Pressure Agent");
        statisticsArea = new JTextArea() {
            @Override
            public void setSize(int width, int height) {
                super.setSize(width, height);
                this.setFont(new Font("Arial", Font.BOLD, statisticsArea.getWidth() / 50));

            }

        };
        statisticsArea.setText("Temperature-Spot-Statistics...");
        JPanel spectrumPanel = new JPanel() {
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
        jFrame.add(spectrumPanel);
        jFrame.add(statisticsArea, BorderLayout.SOUTH);

        jFrame.setSize(800, 600);
        jFrame.setVisible(true);
        //----------------------------------------------------------------------

        managerServiceContract = super.getTinkerforgeManagerServiceContracts()[0];
        TinkerforgeStackIntent intent = new TinkerforgeStackIntent(true, new TinkerforgeStackAddress("localhost"));
        connectTinkerforgeStacksTo(managerServiceContract, new TinkerforgeStackAddress("localhost"));
        contract = new SoundPressureLevelServiceContract("Fud");
        subscribe(contract.EVENT_SPECTRUM, new MessageReceiver() {
            @Override
            public void messageReceived(String topic, byte[] mm) throws Exception {
                SortedSet<SpectrumEvent> spectra = toMessageSet(mm, SpectrumEvent.class);
                if (spectra.size() > 1) {
                    System.out.println("Spectra: " + spectra.size());
                }
                int[] spectrum = spectra.last().value;

                // The following is copied from the example provided by Tinkerforge
                // Use palette mapping to create thermal image coloring 
                for (int i = 0; i < spectrum.length; i++) {
                    //    spectrum[i] = (255 << 24) | (paletteR[spectrum[i]] << 16) | (paletteG[spectrum[i]] << 8) | (paletteB[spectrum[i]] << 0);
                    spectrum[i] = (255 << 24) | spectrum[i] * 1024;
                }
                if (bufferedImage == null) {
                    bufferedImage = new BufferedImage(spectrum.length, 800, BufferedImage.TYPE_INT_ARGB);
                }
                int[] rgbs = bufferedImage.getRGB(0, 1, bufferedImage.getWidth(), bufferedImage.getHeight() - 1, null, 0, spectrum.length);
                for (int i = 0; i < rgbs.length; i++) {
                    rgbs[i] = (255 << 24) | rgbs[i];
                }
                bufferedImage.setRGB(0, 0, bufferedImage.getWidth(), bufferedImage.getHeight() - 1, rgbs, 0, spectrum.length);
                bufferedImage.setRGB(0, bufferedImage.getHeight() - 2, spectrum.length, 1, spectrum, 0, spectrum.length);
                spectrumPanel.updateUI();
            }
        });

        SoundPressureLevelIntent soundPressureIntent = new SoundPressureLevelIntent();
        soundPressureIntent.configuration = new Configuration(FFT.Size1024, Weighting.A);
        soundPressureIntent.spectrumCallbackConfiguration = 1L;
        publishIntent(contract.INTENT, soundPressureIntent);

    }

    public void finish() {
        SoundPressureLevelIntent soundPressureIntent = new SoundPressureLevelIntent();
        soundPressureIntent.spectrumCallbackConfiguration = 0L;
        publishIntent(contract.INTENT, soundPressureIntent);
        super.removeTinkerforgeStackFrom(managerServiceContract, new TinkerforgeStackAddress("localhost"));
        return;
    }

    public static void main(String... args) throws Throwable {
        URI mqttURI = URI.create("tcp://127.0.0.1:1883");
        //URI mqttURI = URI.create("tcp://iot.eclipse.org:1883");
        //URI mqttURI = URI.create("tcp://147.87.116.3:1883");

        if (args.length > 0) {
            mqttURI = URI.create(args[0]);
        } else {
            System.out.printf("Per default, 'tcp://127.0.0.1:1883' is chosen.\nYou can provide another address as first argument i.e.: tcp://iot.eclipse.org:1883\n");
        }
        System.out.printf("\n%s will be used as broker address.\n", mqttURI);
        SoundPressureLevelAgent agent = new SoundPressureLevelAgent(mqttURI);
        System.in.read();
        agent.finish();
    }
}
