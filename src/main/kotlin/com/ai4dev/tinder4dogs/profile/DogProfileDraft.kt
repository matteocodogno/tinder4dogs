package com.ai4dev.tinder4dogs.profile

/**
 * What the model is asked to fill in.
 *
 * Two decisions here are the whole point of the module.
 *
 * **Everything is nullable.** The prompt tells the model to leave what it does
 * not know empty, and a non-null field would quietly turn that instruction into
 * a lie: the model would have to put something there.
 *
 * **The closed vocabularies are STRINGS, not enums.** Naming the allowed values
 * in the prompt improves the first attempt and guarantees nothing. If this type
 * declared `size: DogSize?`, a model talked into answering "quite big" would
 * fail deserialisation and take the whole request down. As a string it arrives,
 * fails to match, and the field is simply left empty by code that decided so on
 * purpose. The model proposes; the application decides.
 */
data class DogProfileDraft(
    val ownerName: String? = null,
    val name: String? = null,
    val breed: String? = null,
    val gender: String? = null,
    val size: String? = null,
    val age: Int? = null,
    val temperament: String? = null,
    val preferences: List<String>? = null,
    /** One short sentence back to the owner: a question, or a refusal. */
    val reply: String? = null,
    /** The model's own report that the message was not about a dog. */
    val outOfScope: Boolean? = null,
)
