package com.example.scratchGame.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record BonusSymbols(
        @JsonProperty("symbols") Map<String, Integer> symbols
) {
    @JsonCreator
    public BonusSymbols {
    }
}
