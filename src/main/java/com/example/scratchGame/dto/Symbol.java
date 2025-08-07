package com.example.scratchGame.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record Symbol(
    @JsonProperty(value = "reward_multiplier") Double rewardMultiplier,
    @JsonProperty(value = "type", required = true) Type type,
    @JsonProperty("impact") String impact,
    @JsonProperty("extra") Integer extra
) {
    @JsonCreator
    public Symbol {}
}
