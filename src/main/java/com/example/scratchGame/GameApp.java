package com.example.scratchGame;

import com.example.scratchGame.dto.Config;
import com.example.scratchGame.dto.Symbol;
import com.example.scratchGame.dto.Type;
import com.example.scratchGame.dto.When;
import com.example.scratchGame.dto.WinCombination;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class GameApp {
    private static final Random RANDOM = new Random();

    public static void main(String[] args) throws IOException {

        String configFileName = null;
        int bettingAmount = 0;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--config":
                    if (i + 1 < args.length) {
                        configFileName = args[++i];
                    }
                    break;
                case "--betting-amount":
                    if (i + 1 < args.length) {
                        bettingAmount = Integer.parseInt(args[++i]);
                    }
                    break;
            }
        }

        if (configFileName == null || bettingAmount <= 0) {
            System.err.println("Usage: java -jar scratchGame.jar --config config.json --betting-amount 100");
            return;
        }

        var objectMapper = new ObjectMapper();
        InputStream is = GameApp.class.getClassLoader().getResourceAsStream(configFileName);

        if (is == null) {
            throw new RuntimeException("config.json not found in resources!");
        }

        Config config = objectMapper.readValue(is, Config.class);

        int rows = config.rows();
        int columns = config.columns();

        var probabilities = config.probabilities();
        Map<String, Integer>[][] standardProbGrid = new Map[rows][columns];

        for (var prob : probabilities.standardSymbols()) {
            standardProbGrid[prob.row()][prob.column()] = prob.symbols();
        }

        // Bonus symbols probabilities
        var bonusSymbols = probabilities.bonusSymbols().symbols();

        String[][] grid = new String[rows][columns];
        int bonusRow = RANDOM.nextInt(rows);
        int bonusCol = RANDOM.nextInt(columns);

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                if (row == bonusRow && col == bonusCol) { //apply note 2
                    grid[row][col] = getRandomSymbol(bonusSymbols);
                } else {
                    var symbolProb = Optional.ofNullable(standardProbGrid[row][col])
                            .orElse(standardProbGrid[0][0]); // fallback to prob[0][0] if there is no more prob
                    grid[row][col] = getRandomSymbol(symbolProb);
                }

            }
        }

        Reward reward = calculateReward(grid, bettingAmount, config);
        ObjectNode rootNode = objectMapper.createObjectNode();
        rootNode.putPOJO("matrix", grid);
        rootNode.put("reward", reward.total());
        rootNode.putPOJO("applied_winning_combinations", reward.linearWinningCombinations());
        rootNode.put("applied_bonus_symbol", reward.appliedBonusSymbol());
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode));
    }

    private static String getRandomSymbol(Map<String, Integer> symbolProbabilities) {
        var symbols = new ArrayList<>(symbolProbabilities.keySet());
        int[] cumulativeWeights = new int[symbols.size()];
        int sum = 0;

        for (int i = 0; i < symbols.size(); i++) {
            sum += symbolProbabilities.get(symbols.get(i));
            cumulativeWeights[i] = sum;
        }

        int randomValue = RANDOM.nextInt(sum);
        int index = Arrays.binarySearch(cumulativeWeights, randomValue);
        if (index < 0) index = -index - 1;

        return symbols.get(index);
    }

    public static Reward calculateReward(String[][] grid, long betAmount, Config config) {
        var symbols = config.symbols();
        var winCombinations = config.winCombinations();

        Map<String, String> symbolWinCombName = new ConcurrentHashMap<>();
        Map<String, List<String>> symbolWinCombLinearNames = new ConcurrentHashMap<>();

        // first construct wining combination that matches symbols, and get highest match for repetition.
        findWinningCombinations(getSymbolCounts(grid), grid, config, symbolWinCombName, symbolWinCombLinearNames);

        double totalReward = computeRewardAmount(symbols, winCombinations, symbolWinCombName, symbolWinCombLinearNames);

        // Apply bonus symbol effects if totalReward > 0
        double multiplier = 1.0;
        double extraBonus = 0.0;
        String appliedBonusSymbol = "None";

        if (totalReward > 0) {
            totalReward *= betAmount; //multiply the total of winning by bet amount (optional probably)

            for (var row : grid) {
                for (var cell : row) {
                    var symbol = symbols.get(cell);
                    if (symbol != null && symbol.type() == Type.bonus) {
                        switch (symbol.impact()) {
                            case "multiply_reward" -> {
                                appliedBonusSymbol = cell;
                                multiplier *= symbol.rewardMultiplier();
                            }
                            case "extra_bonus" -> {
                                appliedBonusSymbol = cell;
                                extraBonus += symbol.extra();
                            }
                        }
                    }
                }
            }
        }

        double finalTotal = totalReward * multiplier + extraBonus;
        return new Reward(finalTotal, appliedBonusSymbol, symbolWinCombLinearNames);
    }

    public static Map<String, Integer> getSymbolCounts(String[][] grid){
        Map<String, Integer> symbolCounts = new HashMap<>();

        for (var row : grid) {
            for (var cell : row) {
                symbolCounts.merge(cell, 1, Integer::sum);
            }
        }
        return symbolCounts;
    }

    private static void findWinningCombinations(Map<String, Integer> symbolCounts, String[][] grid,
                                                Config config, Map<String, String> symbolWinCombName, Map<String, List<String>> symbolWinCombLinearNames
    ) {
        var winCombinations = config.winCombinations();

        int rows = grid.length;
        int cols = grid[0].length;
        int[][] horizontalSumSymbols = new int[rows][cols];
        int[][] verticalSumSymbols = new int[rows][cols];
        int[][] diagonalSumSymbols = new int[rows][cols];

        //parse the grid once so n*t(t can be less than n, as when found comb exit) , rather then checking the win combinations for each symbol
        //the idea is to parse the grid, one time, and for each cell visited, we increment to the specific grid, for horizontal grid, we check rows and parse
        // earlieest column in that row, if match we increment, means we are following the horizontal match adn so on for others
        //but for same_symbol, we can't do it, so each cell found we cache it and increment using map
        for (int row = 0; row < grid.length; row++) {
            for (int col = 0; col < grid[row].length; col++) {
                String currentSymbol = grid[row][col];

                horizontalSumSymbols[row][col] = col > 0 && grid[row][col - 1].equals(currentSymbol) ? horizontalSumSymbols[row][col - 1] + 1 : 1;
                verticalSumSymbols[row][col] = row > 0 && grid[row - 1][col].equals(currentSymbol) ? verticalSumSymbols[row - 1][col] + 1 : 1;
                diagonalSumSymbols[row][col] = col > 0 & row > 0 && grid[row - 1][col - 1].equals(currentSymbol) ? diagonalSumSymbols[row - 1][col - 1] + 1 : 1;

                for (var entryComb : winCombinations.entrySet()) {
                    var winComb = entryComb.getValue();
                    var winCombName = entryComb.getKey();
                    if (winComb.when() == When.linear_symbols) {
                        var coveredAreas = winComb.coveredAreas();
                        int requiredCount = winComb.coveredAreas().size();
                        if ((horizontalSumSymbols[row][col] >= requiredCount &&
                                checkCovered(coveredAreas, row, col, requiredCount, Direction.HORIZONTAL, grid, currentSymbol))
                                || (verticalSumSymbols[row][col] >= requiredCount &&
                                checkCovered(coveredAreas, row, col, requiredCount, Direction.VERTICAL, grid, currentSymbol))
                                || (diagonalSumSymbols[row][col] >= requiredCount &&
                                checkCovered(coveredAreas, row, col, requiredCount, Direction.DIAGONAL, grid, currentSymbol))) {
                            symbolWinCombLinearNames.computeIfAbsent(currentSymbol, k -> new ArrayList<>()).add(winCombName);
                        }
                    } else if (winComb.when() == When.same_symbols) {
                        Integer symbolCount = symbolCounts.getOrDefault(currentSymbol, 0);
                        Integer required = winComb.count();
                        if (required != null && symbolCount >= required) {
                            symbolWinCombName.merge(currentSymbol, winCombName, (existing, newVal) ->
                                    winCombinations.get(existing).count() > winCombinations.get(newVal).count() ? existing : newVal);
                        }
                    }
                }
            }
        }
    }

    private enum Direction {
        HORIZONTAL, VERTICAL, DIAGONAL
    }

    private static boolean checkCovered(List<List<String>> coveredAreas, int latestRow, int latestCol, int neededCount,
                                        Direction direction, String[][] grid, String symbol) {
        List<String> coordinates = new ArrayList<>();
        for (int i = 0; i < neededCount; i++) {
            int r = switch (direction) {
                case HORIZONTAL -> latestRow;
                case VERTICAL -> latestRow - i;
                case DIAGONAL -> latestRow - i;
            };
            int c = switch (direction) {
                case HORIZONTAL -> latestCol - i;
                case VERTICAL -> latestCol;
                case DIAGONAL -> latestCol - i;
            };
            coordinates.add(r + ":" + c);
        }

        for (var area : coveredAreas) {
            if (area.size() == coordinates.size() && new HashSet<>(area).equals(new HashSet<>(coordinates))) {
                return coordinates.stream().allMatch(oneCoordinates -> {
                    String[] parts = oneCoordinates.split(":");
                    int rr = Integer.parseInt(parts[0]);
                    int cc = Integer.parseInt(parts[1]);
                    return grid[rr][cc].equals(symbol);
                });
            }
        }
        return false;
    }

    private static double computeRewardAmount(Map<String, Symbol> symbols, Map<String, WinCombination> winCombinations,
                                              Map<String, String> symbolWinCombName,
                                              Map<String, List<String>> symbolWinCombLinearNames
    ) {
        AtomicReference<Double> totalReward = new AtomicReference<>(0.0);

        symbolWinCombName.forEach((symbolKey, winCombinationName) -> {
            var symbol = symbols.get(symbolKey);
            var combination = winCombinations.get(winCombinationName);
            if(symbol.type().equals(Type.standard)) {
                double totalRewards = symbol.rewardMultiplier() * combination.rewardMultiplier();

                var linearCombos = Optional.ofNullable(symbolWinCombLinearNames.get(symbolKey))
                        .orElse(List.of())
                        .stream()
                        .map(winCombinations::get)
                        .toList();

                if (!linearCombos.isEmpty()) {
                    totalRewards *= linearCombos.stream().mapToDouble(WinCombination::rewardMultiplier).reduce(1, (a, b) -> a * b);
                }

                //add the wincombination same repetition to linear for  output just.
                symbolWinCombLinearNames.computeIfAbsent(symbolKey, k -> new ArrayList<>()).add(winCombinationName);

                double finalTotalRewards = totalRewards;
                totalReward.updateAndGet(v -> v + finalTotalRewards);
            }
        });

        return totalReward.get();
    }

    public record Reward(double total, String appliedBonusSymbol, Map<String, List<String>> linearWinningCombinations) {
    }
}
