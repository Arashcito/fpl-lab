package com.arash.fpllab.model;

/**
 * One row of the Premier League table.
 * crest and form are only filled when the table comes from football-data.org.
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
        int points,
        String form,
        String crest
) {
}
