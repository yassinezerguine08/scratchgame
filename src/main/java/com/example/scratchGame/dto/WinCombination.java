package com.example.scratchGame.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record WinCombination(
    @JsonProperty(value = "reward_multiplier", required = true) double rewardMultiplier,
    @JsonProperty(value = "when", required = true) When when,
    @JsonProperty("count") Integer count,
    @JsonProperty(value = "group", required = true) String group,
    @JsonProperty("covered_areas") List<List<String>> coveredAreas
) {
    @JsonCreator
    public WinCombination {
    }
}
