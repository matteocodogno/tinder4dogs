# Tinder for Dogs — Matching Algorithm

## How matches are calculated
Dogs are matched based on a weighted compatibility score combining four factors:
- **Breed compatibility** (30%): some breeds naturally get along better than others.
  Labradors score high with Goldens; terriers are flagged as low compatibility with toy breeds.
- **Size proximity** (25%): dogs within 10 kg of each other score higher.
- **Energy level** (25%): high-energy dogs matched with low-energy dogs get a penalty.
- **Geographic proximity** (20%): matches within 5 km score maximum points.

## Minimum score for a match to appear
A dog pair must reach a compatibility score of at least 0.65 (out of 1.0)
to appear in each other's feed. Below this threshold the pair is suppressed.

## Why a dog might have few matches
Common causes:
- Profile incomplete (fewer than 3 photos or missing bio)
- Energy level not set — defaults to "medium", which narrows the pool
- Search radius set below 5 km in a low-density area
- Breed flagged as "selective" in the compatibility matrix
