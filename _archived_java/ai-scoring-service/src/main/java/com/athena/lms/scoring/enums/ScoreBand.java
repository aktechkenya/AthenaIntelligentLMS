package com.athena.lms.scoring.enums;

public enum ScoreBand {
    EXCELLENT("750-850"),
    GOOD("670-749"),
    FAIR("580-669"),
    MARGINAL("500-579"),
    POOR("300-499");

    private final String label;

    ScoreBand(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static ScoreBand fromString(String band) {
        if (band == null) {
            return POOR;
        }
        for (ScoreBand sb : values()) {
            if (sb.name().equalsIgnoreCase(band)) {
                return sb;
            }
        }
        return POOR;
    }
}
