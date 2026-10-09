"""The Transcendent power tables (docs/TRANSCENDENT-POWERS.md): signatures, motif twists, motif riders and the bespoke twist of every
curated recipe. Imported by build_fusion_data.py, which validates them against the recipes and writes design/transcendents.json.

A twist is a short list of pulse effects ("ops") from one shared vocabulary, so the 25 motif twists and the 80 bespoke recipe twists are
data, not code. Magnitudes are at 100 percent; the battle module scales them by the harmony benefit share."""

# ---------------------------------------------------------------- the pulse-effect vocabulary and its limits
STATS = ['atk', 'def', 'spa', 'spd', 'spe']
OP_RULES = {
    # op: {field: (low, high)} for ints, or a set of allowed strings
    'status': {'status': {'brn', 'psn', 'par', 'confusion'}, 'chance': (1, 100)},
    'foeStage': {'stat': set(STATS) | {'accuracy'}, 'delta': (-2, -1)},
    'selfStage': {'stat': set(STATS), 'delta': (1, 2), 'cap': (1, 6)},
    'chip': {'pct': (1, 10)},
    'drain': {'pct': (1, 10)},
    'heal': {'pct': (1, 10)},
    'cleanse': {},
    'ward': {},
    'mimic': {},
    'strip': {},
    'unresistedNext': {},
    'shield': {'pct': (1, 50)},
    'boostNext': {'pct': (1, 50)},
    'healBoost': {'pct': (1, 100), 'turns': (1, 5)},
    'hide': {'pct': (1, 30), 'turns': (1, 5)},
    'refine': {'pct': (1, 50), 'turns': (1, 5)},
}


def check_ops(ops, where):
    assert 1 <= len(ops) <= 2, where + ': a twist has one or two effects'
    for op in ops:
        rules = OP_RULES[op['op']]
        assert set(op) - {'op'} == set(rules), f"{where}: {op['op']} needs exactly {sorted(rules)}"
        for field, allowed in rules.items():
            value = op[field]
            if isinstance(allowed, set):
                assert value in allowed, f'{where}: {field}={value}'
            else:
                assert isinstance(value, int) and allowed[0] <= value <= allowed[1], f'{where}: {field}={value}'


def S(status, chance): return {'op': 'status', 'status': status, 'chance': chance}
def FOE(stat, delta=-1): return {'op': 'foeStage', 'stat': stat, 'delta': delta}
def SELF(stat, delta=1, cap=2): return {'op': 'selfStage', 'stat': stat, 'delta': delta, 'cap': cap}
def CHIP(pct): return {'op': 'chip', 'pct': pct}
def DRAIN(pct): return {'op': 'drain', 'pct': pct}
def HEAL(pct): return {'op': 'heal', 'pct': pct}
def SHIELD(pct): return {'op': 'shield', 'pct': pct}
def BOOST(pct): return {'op': 'boostNext', 'pct': pct}
def HEALBOOST(pct, turns=2): return {'op': 'healBoost', 'pct': pct, 'turns': turns}
def HIDE(pct, turns=2): return {'op': 'hide', 'pct': pct, 'turns': turns}
def REFINE(pct, turns=2): return {'op': 'refine', 'pct': pct, 'turns': turns}
CLEANSE = {'op': 'cleanse'}
WARD = {'op': 'ward'}
MIMIC = {'op': 'mimic'}
STRIP = {'op': 'strip'}
UNRESISTED = {'op': 'unresistedNext'}

# ---------------------------------------------------------------- the 25 motif twists (host primary motif)
TWISTS = {
    'EMBER': ('Scorch', [S('brn', 30)]),
    'TIDE': ('Wash', [CLEANSE]),
    'GALE': ('Tailwind', [SELF('spe')]),
    'VERDANT': ('Bloom', [HEAL(4)]),
    'FROST': ('Chill', [FOE('spe')]),
    'STORM': ('Static', [S('par', 30)]),
    'STONE': ('Brace', [SELF('def')]),
    'IRON': ('Plating', [SHIELD(15)]),
    'VENOM': ('Taint', [S('psn', 30)]),
    'SPIRIT': ('Haunt', [DRAIN(4)]),
    'DREAM': ('Daze', [S('confusion', 25)]),
    'WILD': ('Frenzy', [SELF('atk')]),
    'HIVE': ('Sting', [CHIP(3)]),
    'FAE': ('Charm', [FOE('atk')]),
    'WYRM': ("Dragon's Breath", [BOOST(15)]),
    'SHADE': ('Blind', [FOE('accuracy')]),
    'RADIANCE': ('Dawn', [SELF('spa')]),
    'CHIME': ('Dissonance', [FOE('spd')]),
    'GUARD': ('Ward', [WARD]),
    'HARVEST': ('Feast', [HEALBOOST(20)]),
    'COSMOS': ('Warp', [UNRESISTED]),
    'ANCIENT': ('Primeval Hide', [HIDE(10)]),
    'ARTISAN': ('Refine', [REFINE(20)]),
    'TRICKSTER': ('Mimic', [MIMIC]),
    'BRAWL': ('Break Guard', [STRIP]),
}

# ---------------------------------------------------------------- the 25 motif riders (donor primary motif): an existing affix, small
RIDERS = {
    'EMBER': ('smoldering', 10), 'TIDE': ('restorative', 8), 'GALE': ('swift_strike', 4), 'VERDANT': ('steadfast_guard', 5),
    'FROST': ('opening_guard', 6), 'STORM': ('ensnaring', 6), 'STONE': ('physical_bulwark', 4), 'IRON': ('special_bulwark', 4),
    'VENOM': ('venomous', 10), 'SPIRIT': ('stubborn', 5), 'DREAM': ('status_guard', 4), 'WILD': ('keen_edge', 5),
    'HIVE': ('rending', 8), 'FAE': ('bracing_entry', 8), 'WYRM': ('overwhelming_force', 3), 'SHADE': ('opening_strike', 6),
    'RADIANCE': ('healthy_force', 4), 'CHIME': ('momentum', 3), 'GUARD': ('iron_resolve', 4), 'HARVEST': ('triumphant', 8),
    'COSMOS': ('super_effective_force', 4), 'ANCIENT': ('resilient_hide', 4), 'ARTISAN': ('kindred_force', 5),
    'TRICKSTER': ('wardstone', 6), 'BRAWL': ('executioner', 6),
}

# ---------------------------------------------------------------- the 21 signatures (id matches the base Transcendent id)
# id: (pulse, core, clutch, drawback) - English at 100 percent strength, for the hover and the design record
SIGNATURES = {
    'phoenix_cinder': ('STRUCK', 'Attackers that hit you while you are below 50% HP are burned.', 'A lethal hit leaves you at 20% HP instead of fainting.', 'Healing cannot raise you above 70% of max HP.'),
    'plague_pyre': ('TICK', 'Burned or poisoned foes take an extra 3% of max HP each turn.', '', 'Your direct damage is 10% lower.'),
    'wildfire_crown': ('HIT', 'Fire moves deal 20% more; while any weather is active all your moves deal 10% more.', '', 'Water and Ice moves deal 25% less.'),
    'brand_of_ruin': ('HIT', 'A damaging hit Brands the foe for 3 turns: it takes 10% more from you and 30% more from burn and bleed.', '', 'A new Brand costs you 3% of max HP.'),
    'titans_forge': ('HIT', 'A super-effective hit adds 8% of your max HP in damage and has a 25% chance to burn.', '', 'You lose 3% of max HP each turn while above 60% HP.'),
    'gilded_ember': ('KO', 'Burn damage you inflict +30%; each KO adds +10% damage (3 stacks, lost on switching out); item rewards +10%.', '', 'You take 15% more damage from Water moves.'),
    'dying_bloom': ('STRUCK', 'Attackers that hit you while you are below 50% HP are poisoned; your poison ramps twice as fast then.', 'A lethal hit leaves you at 1 HP.', 'Healing you receive is 35% lower.'),
    'eye_of_the_storm': ('TICK', 'While any weather is active, heal 3% of max HP each turn and take 12% less damage.', 'A lethal hit leaves you at 1 HP.', 'With no weather you take 12% more damage.'),
    'martyrs_edge': ('HIT', 'Physical hits bleed; you deal 12% more to bleeding foes.', 'A lethal hit leaves you at 1 HP and your next hit is a guaranteed critical.', 'Your bleeds are weaker (stack weights 1, 0.8, 0.6).'),
    'colossus_vow': ('STRUCK', 'You take 15% less from super-effective hits and counter any super-effective hit for 6% of your max HP.', 'A lethal hit leaves you at 1 HP.', 'Your Speed is 15% lower.'),
    'last_gamble': ('KO', 'Item rewards +15%.', 'A lethal hit leaves you at 30% HP and your next 2 moves deal 30% more.', 'After the clutch fires you take 25% more damage for the rest of the battle.'),
    'miasma_front': ('TICK', 'While any weather is active every foe takes 2.5% of max HP each turn, rising 1% a turn to 5%; poison you inflict +20%.', '', 'With no weather you lose 2% of max HP each turn.'),
    'festering_gash': ('HIT', 'Physical hits bleed; each turn a foe bleeds its bleed damage rises 15% (to 60%); you heal 25% of the bleed damage dealt.', '', 'You take 20% more damage from Psychic moves.'),
    'blighted_giant': ('HIT', 'A super-effective hit adds 7% of your max HP in damage and poisons the foe; poison damage you inflict +25%.', '', 'Healing you receive is 60% lower.'),
    'fortunes_rot': ('TICK', 'Foes you have poisoned lose an extra 3% of max HP each turn; item rewards +15%.', '', 'You take 20% more damage.'),
    'lightning_rend': ('HIT', "Moves of the active weather's type, or Electric moves, deal 25% more and cause a bleed whatever their category.", '', 'With no weather your direct damage is 20% lower.'),
    'tempest_colossus': ('HIT', 'Weather-type moves deal 20% more; a super-effective hit adds 7% of your max HP in damage.', '', 'Your Speed is 12% lower.'),
    'skyfall_fortune': ('KO', 'Weather-type moves deal 25% more; each KO while a weather is active adds 12% damage (3 stacks); item rewards +15%.', '', 'With no weather you take 20% more damage.'),
    'breakers_might': ('HIT', 'Physical hits bleed; a super-effective hit adds 8% of your max HP in damage, and 10% more if the foe is bleeding.', '', 'Healing you receive is 50% lower.'),
    'wager_of_blood': ('KO', 'Physical hits bleed; each KO restores 10% of your max HP; item rewards +15%.', '', 'Every physical move you use costs you 2% of max HP.'),
    'jackpot_titan': ('KO', 'A super-effective hit adds 10% of your max HP in damage; item rewards +20%.', '', 'You take 22% more damage.'),
}

# ---------------------------------------------------------------- the bespoke twist of every curated recipe (pairs, then groups)
RECIPE_TWISTS = {
    # pairs
    'primordial_clash': ('Cataclysm', [CHIP(4), SELF('def')]),
    'hand_of_creation': ('Fold of Space-Time', [SHIELD(20), UNRESISTED]),
    'taijitu': ('Yin and Yang', [DRAIN(4), BOOST(10)]),
    'white_boundary': ('Truth Frozen', [S('brn', 30), FOE('spe')]),
    'black_boundary': ('Ideal Frozen', [S('par', 30), FOE('spe')]),
    'crowned_pair': ('Sword and Shield', [BOOST(15), SHIELD(15)]),
    'wheel_of_life': ('Life and Death', [HEAL(4), DRAIN(4)]),
    'eclipse': ('Totality', [FOE('accuracy'), SELF('spa')]),
    'dusk_mane': ('Prism Feed', [SELF('atk'), BOOST(10)]),
    'dawn_wings': ('Prism Veil', [CLEANSE, FOE('accuracy')]),
    'eon_bond': ('Shared Dream', [HEAL(4), CLEANSE]),
    'rainbow_tower': ('Seven Colours', [HEAL(3), SELF('spa')]),
    'original_and_copy': ('Perfect Copy', [MIMIC, STRIP]),
    'ice_rider': ('Rider of Ice', [FOE('spe'), BOOST(15)]),
    'shadow_rider': ('Rider of Shadow', [FOE('accuracy'), DRAIN(4)]),
    'past_and_future': ('Time Fold', [HIDE(10), BOOST(15)]),
    'origin_and_shadow': ('Distortion', [STRIP, S('confusion', 25)]),
    'elder_tusk': ('Mammoth Charge', [SELF('atk'), HIDE(10)]),
    'wheel_of_donphan': ('Rolling Armour', [SELF('def'), BOOST(10)]),
    'tusk_and_tread': ('Tusk and Tread', [SHIELD(15), SELF('atk')]),
    'lullaby_unbound': ('Scream Lullaby', [S('confusion', 25), FOE('spd')]),
    'gift_of_tomorrow': ('Gift of Tomorrow', [HEAL(4), FOE('spe')]),
    'sumo_machine': ('Piston Slap', [BOOST(15), FOE('spe')]),
    'iron_hydra': ('Three Heads Online', [CHIP(3), STRIP]),
    'foo_fighter': ('Sun Probe', [S('brn', 30), FOE('accuracy')]),
    'dawn_of_moths': ('Sun Dust', [CHIP(3), S('brn', 20)]),
    'moth_across_ages': ('Dust of Ages', [HIDE(10), S('brn', 20)]),
    'mecha_kaiju': ('Stomp Protocol', [SHIELD(15), SELF('atk')]),
    'paladin_oath': ("Knight's Pledge", [WARD, SHIELD(15)]),
    'paladin_blade': ("Knight's Edge", [WARD, BOOST(15)]),
    'flying_head': ('Wail', [S('confusion', 25), DRAIN(3)]),
    'ironsand_age': ('Magnetic Storm', [S('par', 30), FOE('spe')]),
    'mold_beneath': ('Spore Rot', [S('psn', 30), HEAL(3)]),
    'dragon_of_the_dream': ('Dream Wyrm', [SELF('spe'), BOOST(15)]),
    'purifying_wake': ('Clear Tide', [CLEANSE, HEAL(4)]),
    'shining_blades': ('Julienne', [BOOST(20), FOE('def')]),
    'regenerated_pyre': ('Reborn Flame', [S('brn', 30), HEAL(3)]),
    'thunder_lizard': ('Thunderous Step', [S('par', 30), CHIP(3)]),
    'blade_of_the_cavern': ('Spade Rush', [SELF('def'), BOOST(15)]),
    'crown_of_iron': ('Crowned Resolve', [SHIELD(15), STRIP]),
    'prehistoric_skies': ('Ancient Updraft', [SELF('spe'), HIDE(10)]),
    'two_golems': ('Clay and Stone', [SELF('def'), SHIELD(15)]),
    'borrowed_face': ('Borrowed Smile', [MIMIC, HEAL(3)]),
    'blade_and_pincer': ('Mantis Cut', [BOOST(15), FOE('def')]),
    'moon_shadow': ('Moonlit Mimic', [MIMIC, FOE('accuracy')]),
    # groups
    'weather_pact': ('Pact of Sky, Sea and Land', [UNRESISTED, CHIP(3)]),
    'legendary_flight': ('Wings Unfurled', [SELF('spe'), FOE('spe')]),
    'roaming_beasts': ('Brass Tower Rebirth', [HEAL(4), SELF('spe')]),
    'titan_golems': ('Seal Unbroken', [SHIELD(20), SELF('def')]),
    'lake_guardians': ('Triple Lake', [CLEANSE, WARD]),
    'swords_of_justice': ('Sworn Blades', [BOOST(15), SHIELD(15)]),
    'forces_of_nature': ('Tempest Cycle', [S('par', 25), SELF('spe')]),
    'guardian_deities': ('Island Challenge', [WARD, HEAL(3)]),
    'treasures_of_ruin': ('Ruinous Aura', [CHIP(3), FOE('spd')]),
    'beyond_the_wormhole': ('Ultra Space', [UNRESISTED, FOE('accuracy')]),
    'retainers_of_the_ogre': ('Reversed Tale', [S('psn', 30), MIMIC]),
    'pseudo_summit': ('Summit Power', [SELF('atk'), HIDE(10)]),
    'kanto_triad': ('First Partners', [HEAL(4), SELF('atk')]),
    'johto_triad': ('Johto Bond', [HEAL(3), SHIELD(15)]),
    'hoenn_triad': ('Hoenn Surge', [SELF('spe'), BOOST(10)]),
    'sinnoh_triad': ('Sinnoh Unity', [SELF('def'), HEAL(3)]),
    'unova_triad': ('Unova Resolve', [BOOST(15), CLEANSE]),
    'kalos_triad': ('Kalos Elegance', [SELF('spa'), FOE('accuracy')]),
    'alola_triad': ('Alola Pride', [SELF('atk'), SHIELD(10)]),
    'galar_triad': ('Galar Champion', [BOOST(15), SELF('spe')]),
    'paldea_triad': ('Paldea Spirit', [HEAL(4), BOOST(10)]),
    'kitsune_court': ('Nine Tails', [S('confusion', 25), S('brn', 25)]),
    'bushido_court': ('Way of the Blade', [BOOST(15), STRIP]),
    'dragon_gate': ('Leap the Falls', [SELF('atk'), HEAL(4)]),
    'moon_court': ('Moonlight', [HEAL(4), FOE('accuracy')]),
    'sun_court': ('Sunlight', [SELF('spa'), S('brn', 20)]),
    'animated_earth': ('Clay Awakened', [SHIELD(20), SELF('def')]),
    'halloween_hollow': ('Trick and Treat', [S('confusion', 25), HEAL(3)]),
    'tea_ceremony': ('Steeped', [HEALBOOST(20), CLEANSE]),
    'dessert_table': ('Sweet Tooth', [HEALBOOST(25), FOE('spe')]),
    'machine_choir': ('Synchronised', [S('par', 25), SELF('spe')]),
    'three_wise_monkeys': ('Hear No Evil', [WARD, FOE('spd')]),
    'fossil_revival': ('Revived Might', [HIDE(10), SELF('atk')]),
    'galar_fossils': ('Mismatched Parts', [MIMIC, CHIP(3)]),
    'mythical_dawn': ('Elusive', [FOE('accuracy'), WARD]),
}

for _name, (_label, _ops) in list(TWISTS.items()) + list(RECIPE_TWISTS.items()):
    check_ops(_ops, _name)
assert len(TWISTS) == 25 and len(RIDERS) == 25 and len(SIGNATURES) == 21 and len(RECIPE_TWISTS) == 80
assert {p for p, *_ in SIGNATURES.values()} <= {'HIT', 'STRUCK', 'KO', 'LAST', 'TICK'}
