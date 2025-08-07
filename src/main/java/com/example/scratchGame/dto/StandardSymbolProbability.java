package com.example.scratchGame.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record StandardSymbolProbability(
        @JsonProperty("column") int column,
        @JsonProperty("row") int row,
        @JsonProperty("symbols") Map<String, Integer> symbols
) {
    @JsonCreator
    public StandardSymbolProbability {
    }
}
