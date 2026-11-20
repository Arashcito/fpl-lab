package com.arash.fpllab.model;

/**
 * One row of the Premier League table, calculated from finished fixtures.
 */
public record TeamWithStanding(
        int position,
        int teamId,
        String name,
        String shortName,
        int played,
        int won,
        int drawn,
        int lost,
        int goalsFor,
        int goalsAgainst,
        int goalDifference,
        int points
) {
}
