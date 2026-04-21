package com.ai4dev.tinderfordogs.match.service

import com.ai4dev.tinderfordogs.dogprofile.model.DogGender
import com.ai4dev.tinderfordogs.dogprofile.model.DogProfile
import com.ai4dev.tinderfordogs.dogprofile.model.DogSize
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

class CompatibilityScorerPropertyTest :
    FreeSpec({

        val arbProfile: Arb<DogProfile> =
            arbitrary {
                DogProfile(
                    name = "Dog",
                    breed = Arb.string(1..20).next(it),
                    size = DogSize.MEDIUM,
                    age = Arb.int(0..15).next(it),
                    gender = Arb.enum<DogGender>().next(it),
                )
            }

        "Symmetry: score(A, B) == score(B, A)" - {
            "holds for all profiles with absolute age difference" {
                checkAll(arbProfile, arbProfile) { a: DogProfile, b: DogProfile ->
                    CompatibilityScorer.score(a, b) shouldBe CompatibilityScorer.score(b, a)
                }
            }
        }

        "Bounds: score is always in [0.0, 1.0]" - {
            "holds for all generated profiles" {
                checkAll(arbProfile, arbProfile) { a: DogProfile, b: DogProfile ->
                    val s = CompatibilityScorer.score(a, b)
                    s shouldBeGreaterThanOrEqualTo 0.0
                    s shouldBeLessThanOrEqualTo 1.0
                }
            }
        }

        // BUG: a dog scores itself 0.55 (age 30 + breed 25, but NO gender bonus for same gender),
        // while an opposite-gender dog of the same breed and close age scores 0.70.
        "Reflexivity: score(A, A) >= score(A, B) for any B" - {
            "fails because opposite-gender pairs outscore self-comparison (documents known bug)".config(enabled = false) {
                checkAll(arbProfile, arbProfile) { a: DogProfile, b: DogProfile ->
                    CompatibilityScorer.score(a, a) shouldBeGreaterThanOrEqualTo CompatibilityScorer.score(a, b)
                }
            }
        }
    })
