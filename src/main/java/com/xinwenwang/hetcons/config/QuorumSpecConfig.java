package com.xinwenwang.hetcons.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.isaacsheff.charlotte.proto.HetconsObserverQuorum;

@JsonIgnoreProperties(ignoreUnknown = true)
public class QuorumSpecConfig {


    /* the name of the pool of participants*/
    /* TODO: in the future, we might want this to be a reference to a ChainName.quorumName format */
    @JsonProperty("base")
    String base;

    /* this is the k in "any k out of n" where n is the size of end - start. If end -start = 0 then config will use all participants in the pool */
    @JsonProperty("size")
    int size;

    public QuorumSpecConfig(@JsonProperty("base") String base,
                            @JsonProperty("size") int size) {
        this.base = base;
        this.size  = size;
    }

    public HetconsObserverQuorum.Spec toQuorumSpec() {
        return HetconsObserverQuorum.Spec.newBuilder()
                .setBase(base)
                .setSize(size)
                .build();
    }
}
