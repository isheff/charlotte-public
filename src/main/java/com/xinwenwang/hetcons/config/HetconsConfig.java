package com.xinwenwang.hetcons.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.isaacsheff.charlotte.proto.Hash;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

public class HetconsConfig {


    private static final Logger logger = Logger.getLogger(HetconsConfig.class.getName());


    protected static File configFileDirectory;
    private HashMap<String, ChainConfig> configHashMap;


    public boolean loadChain(String name) {
        ChainConfig config = parseHetconsYaml(Paths.get(configFileDirectory.toString(), name+".yaml"));
        if (config != null)
            configHashMap.put(config.getId(), config);
        return config != null;
    }

    public ChainConfig getChainConfig(String hash) {
        return configHashMap.get(hash);
    }

    public ChainConfig getChainConfig(Hash hash) {
        return configHashMap.get(hash.getSha3().toStringUtf8());
    }

    public void setConfigFileDirectory(File configFileDirectory) {
        configFileDirectory = configFileDirectory;
    }

//    public HetconsConfig(File configFileDirectory) {
//        configFileDirectory = configFileDirectory;
//    }
//
//    public HetconsConfig(String configFileDirectory) {
//        HetconsConfig.configFileDirectory = new File(configFileDirectory);
//    }

    public HetconsConfig() {
        if (HetconsConfig.configFileDirectory == null)
            this.configFileDirectory = new File(".");
        this.configHashMap = new HashMap<>();
    }

    public static void setConfigFileDirectory(String fileDirectory) {
        HetconsConfig.configFileDirectory = new File(fileDirectory);
    }

    public static File getConfigFileDirectory() {
        return configFileDirectory;
    }

    /**
     * Basically copy from class charlotte.yaml.Config
     * @param path to the config file.
     * @return ChainConfig instance if parse successfully otherwise null.
     */
    static private ChainConfig parseHetconsYaml(Path path) {
        try {
            return (new ObjectMapper(new YAMLFactory())).readValue(path.toFile(), ChainConfig.class);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Something went wrong while reading the YAML config file: " + path, e);
        }
        return null;
    }

    static public void updateChainConfig() {
        //TODO: May or may not need this
    }



}
