package com.example.scratchGame.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record Probabilities(
        @JsonProperty("standard_symbols") List<StandardSymbolProbability> standardSymbols,
        @JsonProperty("bonus_symbols") BonusSymbols bonusSymbols
) {
    @JsonCreator
    public Probabilities {
    }
}
