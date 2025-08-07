package com.example.scratchGame.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record Config(
        @JsonProperty(value = "columns", defaultValue = "3") int columns,
        @JsonProperty(value = "rows", defaultValue = "3") int rows,
        @JsonProperty(value = "symbols", required = true) Map<String, Symbol> symbols,
        @JsonProperty(value = "probabilities", required = true) Probabilities probabilities,
        @JsonProperty(value = "win_combinations") Map<String, WinCombination> winCombinations
) {
    @JsonCreator
    public Config {
    }
}
