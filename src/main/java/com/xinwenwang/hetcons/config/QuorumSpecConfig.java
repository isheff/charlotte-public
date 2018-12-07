package com.xinwenwang.hetcons.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.isaacsheff.charlotte.proto.HetconsObserverQuorum;

@JsonIgnoreProperties(ignoreUnknown = true)
public class QuorumSpecConfig {


    /* the name of the pool of participants*/
    @JsonProperty("base")
    String base;

    @JsonProperty("start")
    int start;

    @JsonProperty("end")
    int end;

    /* this is the k in "any k out of n" where n is the size of end - start. If end -start = 0 then config will use all participants in the pool */
    @JsonProperty("size")
    int size;

    public QuorumSpecConfig(@JsonProperty("base") String base,
                            @JsonProperty("start") int start,
                            @JsonProperty("end") int end,
                            @JsonProperty("size") int size) {
        this.base = base;
        this.start = start;
        this.end = end;
        this.size  = size;
    }

    public HetconsObserverQuorum.Spec toQuorumSpec() {
        return HetconsObserverQuorum.Spec.newBuilder()
                .setBase(base)
                .setStart(start)
                .setEnd(end)
                .setSize(size)
                .build();
    }
}
