package com.isaacsheff.charlotte.experiments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.isaacsheff.charlotte.fern.HetconsFern;
import com.isaacsheff.charlotte.node.HetconsParticipantNodeForFern;
import com.isaacsheff.charlotte.yaml.Config;
import com.xinwenwang.hetcons.HetconsParticipantService;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static java.lang.Integer.parseInt;

public class HetconsExperimentFernNode extends HetconsParticipantNodeForFern {

    public HetconsExperimentFernNode() {
        super(null, null, null);
    }

    public static void launchServer(String filename)  throws InterruptedException, IOException {
        final HetconsExperimentFernConfig config =
                (new ObjectMapper(new YAMLFactory())).readValue(Paths.get(filename).toFile(), HetconsExperimentFernConfig.class);

        Config fernConfig = new Config(config, Paths.get(filename).getParent());

        HetconsParticipantNodeForFern node = new HetconsParticipantNodeForFern(fernConfig, null, null);

        HetconsFern fern = new HetconsFern(node);

        final Thread thread = new Thread(HetconsFern.getFernNode(fern));
        thread.start();
//        logger.info("TimestampExperimentFern service started on new thread");
        thread.join();
//        return nodeService;
    }

    public static void main(String[] args) {
        Logger.getLogger(HetconsParticipantService.class.getName()).info("Hetcons Version 0.0.1");
        try {
            launchServer(args[0]);
            if (args.length < 3) {
                TimeUnit.SECONDS.sleep(Integer.MAX_VALUE);
            } else {
                TimeUnit.SECONDS.sleep(parseInt(args[2]));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
