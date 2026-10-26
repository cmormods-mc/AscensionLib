package com.cobbleascend.store;

import java.util.UUID;

/**
 * The Haunting a Pokemon holds (docs/HALLOWEEN-EVENT-DESIGN.md): which one, how many times it has been rolled (which sets the price of the next
 * roll), and the operation that last rolled it. Kept in its own table, keyed by the Pokemon, so it follows the Pokemon through trades the way its
 * profile does and never competes with the profile's ordinary slots.
 */
public record HauntingRecord(UUID pokemonId, String hauntingId, int rolls, UUID operationId, long updatedAt) {}
