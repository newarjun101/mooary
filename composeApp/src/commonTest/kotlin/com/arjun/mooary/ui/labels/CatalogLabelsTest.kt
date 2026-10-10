package com.arjun.mooary.ui.labels

import com.arjun.mooary.domain.model.EmotionCatalog
import com.arjun.mooary.domain.model.Mood
import com.arjun.mooary.domain.model.MoodDescriptor
import com.arjun.mooary.resources.Res
import com.arjun.mooary.resources.mood_unknown
import com.arjun.mooary.domain.model.MoodCatalog
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The catalogue and the translations are two separate lists that have to agree.
 *
 * `MoodCatalog` names every mood with a `nameKey` string and `EmotionCatalog` names every
 * emotion with an id, but the UI can only render keys that [CatalogLabels] has mapped to a
 * resource. Nothing enforces that: adding a mood compiles fine, renders `mood_new_mood`
 * as a fallback, and the screen quietly shows "Unrecognised mood" for it. These tests are
 * the enforcement, and they fail in the same build as the change that breaks them.
 */
class CatalogLabelsTest {

    @Test
    fun every_mood_key_has_a_resource() {
        val mappings = catalogLabelMappings.getValue("mood")
        val missing = MoodCatalog.all.filter { it.nameKey !in mappings }
        assertEquals(emptyList<String>(), missing.map { it.mood.name })
    }

    @Test
    fun no_mood_resource_is_orphaned() {
        val declared = MoodCatalog.all.map { it.nameKey }.toSet()
        val mapped = catalogLabelMappings.getValue("mood").keys
        assertEquals(emptySet<String>(), mapped - declared)
    }

    @Test
    fun every_emotion_id_has_a_resource() {
        val mappings = catalogLabelMappings.getValue("emotion")
        val missing = EmotionCatalog.all.filter { it.id !in mappings }
        assertEquals(emptyList<String>(), missing.map { it.id })
    }

    @Test
    fun no_emotion_resource_is_orphaned() {
        val declared = EmotionCatalog.all.map { it.id }.toSet()
        val mapped = catalogLabelMappings.getValue("emotion").keys
        assertEquals(emptySet<String>(), mapped - declared)
    }

    @Test
    fun every_emotion_group_key_has_a_resource() {
        val mappings = catalogLabelMappings.getValue("emotion_group")
        val missing = EmotionCatalog.groups.map { it.first }.filter { it !in mappings }
        assertEquals(emptyList(), missing)
    }

    @Test
    fun mood_keys_are_unique() {
        // Two moods sharing a key would silently render the same label for both.
        val keys = MoodCatalog.all.map { it.nameKey }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun mood_ids_are_unique() {
        val ids = Mood.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun emotion_ids_are_unique() {
        val ids = EmotionCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun shared_ids_map_to_different_resources() {
        // "tired", "sad", "angry" and "anxious" are both moods and emotions. They share ids
        // but must not share a label, or the picker would offer the same word twice.
        val shared = Mood.entries.map { it.id }
            .filter { id -> EmotionCatalog.byId.containsKey(id) }
        val collisions = shared.filter { id ->
            catalogLabelMappings.getValue("mood")["mood_$id"] ==
                catalogLabelMappings.getValue("emotion")[id]
        }
        assertEquals(emptyList(), collisions)
    }

    @Test
    fun every_mood_is_reachable_through_the_catalogue() {
        val catalogued = MoodCatalog.all.map { it.mood }.toSet()
        assertEquals(Mood.entries.toSet(), catalogued)
    }

    @Test
    fun an_unknown_mood_id_resolves_to_a_renderable_label() {
        // A mood id written by a newer client must not make a synced entry unreadable, so
        // it resolves to the sentinel and renders as a real name. The tradeoff is that the
        // value is silently rewritten to neutral rather than flagged as unrecognised.
        val resolved = Mood.fromId("euphoric")
        assertEquals(Mood.UNKNOWN, resolved)
        assertEquals("mood_neutral", MoodCatalog.descriptorFor(resolved).nameKey)
    }

    @Test
    fun an_unmapped_descriptor_key_still_has_a_label() {
        // Reachable only for a hand-built descriptor, but a missing key must degrade to a
        // label rather than to a raw key or a crash.
        val stray = MoodDescriptor(
            mood = Mood.EXCITED,
            nameKey = "mood_from_the_future",
            descriptionKey = "mood_excited_description",
        )
        assertEquals(Res.string.mood_unknown, moodNameResource(stray))
    }
}
