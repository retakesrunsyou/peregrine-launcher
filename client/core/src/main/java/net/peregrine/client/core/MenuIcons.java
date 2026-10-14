package net.peregrine.client.core;

import java.util.HashMap;
import java.util.Map;

/** Small line icons for menu buttons, drawn from 9x9 pixel maps (no textures needed). */
public final class MenuIcons {

    private static final Map<String, String[]> ICONS = new HashMap<String, String[]>();

    static {
        ICONS.put("play", new String[] {
            ".........",
            "..#......",
            "..##.....",
            "..#.#....",
            "..#..#...",
            "..#.#....",
            "..##.....",
            "..#......",
            "........."});
        ICONS.put("controller", new String[] {
            ".........",
            ".........",
            ".#######.",
            "#.#....##",
            "###..#..#",
            "#.#...#.#",
            "#.......#",
            "##.###.##",
            "........."});
        ICONS.put("globe", new String[] {
            "...###...",
            ".##.#.##.",
            ".#..#..#.",
            "#########",
            "#...#...#",
            "#########",
            ".#..#..#.",
            ".##.#.##.",
            "...###..."});
        ICONS.put("grid", new String[] {
            ".........",
            ".###.###.",
            ".#.#.#.#.",
            ".###.###.",
            ".........",
            ".###.###.",
            ".#.#.#.#.",
            ".###.###.",
            "........."});
        ICONS.put("sliders", new String[] {
            ".........",
            "..#......",
            "#####.###",
            "..#......",
            "......#..",
            "####.####",
            "......#..",
            "...#.....",
            "#######.#"});
        ICONS.put("power", new String[] {
            "....#....",
            ".#..#..#.",
            "#...#...#",
            "#...#...#",
            "#.......#",
            "#.......#",
            ".#.....#.",
            "..#####..",
            "........."});
        ICONS.put("castle", new String[] {
            ".........",
            "#.#.#.#.#",
            "#########",
            ".#.....#.",
            ".#.....#.",
            ".#..#..#.",
            ".#.###.#.",
            "####.####",
            "........."});
        ICONS.put("language", new String[] {
            ".........",
            "####.....",
            ".#.......",
            "###..###.",
            "#.#.#..#.",
            ".#..####.",
            "....#..#.",
            "....#..#.",
            "........."});
        ICONS.put("person", new String[] {
            "....#....",
            "...###...",
            "....#....",
            "#########",
            "....#....",
            "....#....",
            "...#.#...",
            "..#...#..",
            ".#.....#."});
        ICONS.put("leave", new String[] {
            "#####....",
            "#........",
            "#....#...",
            "#.....#..",
            "#.#######",
            "#.....#..",
            "#....#...",
            "#........",
            "#####...."});
        ICONS.put("more", new String[] {
            ".........",
            ".........",
            ".........",
            ".........",
            "##..#..##",
            ".........",
            ".........",
            ".........",
            "........."});
        ICONS.put("chevron", new String[] {
            "..#......",
            "...#.....",
            "....#....",
            ".....#...",
            "......#..",
            ".....#...",
            "....#....",
            "...#.....",
            "..#......"});
    }

    private MenuIcons() {
    }

    /** Draws an icon 9 pixels square (times scale) with its top-left at x, y. */
    public static void draw(Draw d, String name, int x, int y, int color) {
        String[] rows = ICONS.get(name);
        if (rows == null) {
            return;
        }
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            int start = -1;
            for (int c = 0; c <= row.length(); c++) {
                boolean on = c < row.length() && row.charAt(c) == '#';
                if (on && start < 0) {
                    start = c;
                } else if (!on && start >= 0) {
                    d.rect(x + start, y + r, c - start, 1, color);
                    start = -1;
                }
            }
        }
    }

    public static boolean has(String name) {
        return ICONS.containsKey(name);
    }
}
