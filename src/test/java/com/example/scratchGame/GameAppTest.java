package com.example.scratchGame;

import com.example.scratchGame.dto.Config;
import com.example.scratchGame.dto.Symbol;
import com.example.scratchGame.dto.Type;
import com.example.scratchGame.dto.When;
import com.example.scratchGame.dto.WinCombination;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GameAppTest {

    private Config config;
    private Map<String, Symbol> symbols;
    private Map<String, WinCombination> winCombinations;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        setupTestConfig();
    }

    private void setupTestConfig() {
        // Setup symbols
        symbols = new HashMap<>();
        symbols.put("A", new Symbol(10.0, Type.standard,  null, null));
        symbols.put("B", new Symbol(3.0, Type.standard, null, null));
        symbols.put("C", new Symbol(2.0, Type.standard,  null, null));

        winCombinations = new HashMap<>();

        // Same symbol combinations
        winCombinations.put("same_symbol_3_times",
                new WinCombination(2.0, When.same_symbols, 3, null, null));

        winCombinations.put("same_symbols_vertically",
                new WinCombination(2.0, When.linear_symbols, null,
                        null,
                        Arrays.asList(
                                Arrays.asList("0:0", "1:0", "2:0"),
                                Arrays.asList("0:1", "1:1", "2:1"),
                                Arrays.asList("0:2", "1:2", "2:2")
                        )));

        // Create mock config
        config = mock(Config.class);
        when(config.symbols()).thenReturn(symbols);
        when(config.winCombinations()).thenReturn(winCombinations);
        when(config.rows()).thenReturn(3);
        when(config.columns()).thenReturn(3);
    }

    @Test
    @DisplayName("Should calculate reward for same symbols winning combination")
    void testCalculateRewardSameSymbols() {
        String[][] grid = {
                {"A", "B", "C"},
                {"A", "C", "B"},
                {"A", "B", "C"}
        };
        long betAmount = 100;

        GameApp.Reward reward = GameApp.calculateReward(grid, betAmount, config);

        // Then: A, B, C, appears 3 times, reward = (symbol(10.0) * combination(2.0) * (2 same symbol vertically) + (3 * 2) + (2* 2)) * (100 betamount) = 5000
        assertEquals(5000, reward.total());
        assertEquals("None", reward.appliedBonusSymbol());
        assertFalse(reward.linearWinningCombinations().isEmpty());
    }
}