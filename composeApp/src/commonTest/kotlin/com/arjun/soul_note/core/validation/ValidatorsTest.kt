package com.arjun.soul_note.core.validation

import com.arjun.soul_note.core.error.ErrorKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ValidatorsTest {

    @Test
    fun `email accepts ordinary addresses`() {
        listOf(
            "user@example.com",
            "first.last@example.co.uk",
            "user+tag@sub.domain.org",
            "  spaced@example.com  ",
        ).forEach { candidate ->
            assertTrue(Validators.isValidEmail(candidate), "expected $candidate to be valid")
        }
    }

    @Test
    fun `email rejects the shapes Firebase would reject`() {
        listOf(
            "",
            "a@b", // no dot in the domain
            "no-at-sign.example.com",
            "@example.com", // empty local part
            "user@", // empty domain
            "user@.com", // domain starts with a dot
            "user@example.", // domain ends with a dot
            "user@exa..mple.com", // double dot
            "us er@example.com", // embedded whitespace
            "user@@example.com", // two ats
            "ab", // below Firebase's six-character minimum
        ).forEach { candidate ->
            assertFalse(Validators.isValidEmail(candidate), "expected $candidate to be invalid")
        }
    }

    @Test
    fun `email result carries the email field and the invalid-address key`() {
        val result = Validators.email("nope")
        val invalid = assertIs<ValidationResult.Invalid>(result)
        assertEquals(Field.EMAIL, invalid.errors.errors.single().field)
        assertEquals(ErrorKeys.INVALID_EMAIL, invalid.errors.errors.single().reasonKey)
    }

    @Test
    fun `password bounds match the Firebase requirements`() {
        assertFalse(Validators.isValidPassword("short"))
        assertTrue(Validators.isValidPassword("12345678"))
        assertTrue(Validators.isValidPassword("a".repeat(Limits.PASSWORD_MAX_LENGTH)))
        assertFalse(Validators.isValidPassword("a".repeat(Limits.PASSWORD_MAX_LENGTH + 1)))
    }

    @Test
    fun `title rejects blank and over-long input`() {
        assertIs<ValidationResult.Invalid>(Validators.diaryTitle("   "))
        assertIs<ValidationResult.Valid>(Validators.diaryTitle("A day at the beach"))

        val tooLong = assertIs<ValidationResult.Invalid>(
            Validators.diaryTitle("t".repeat(Limits.TITLE_MAX_LENGTH + 1)),
        )
        assertEquals(ErrorKeys.TITLE_TOO_LONG, tooLong.errors.errors.single().reasonKey)
    }

    @Test
    fun `title length is measured before trimming so padding cannot smuggle content past the limit`() {
        // A 200-character title padded with spaces is over the limit once measured: the
        // repository enforces the same number, so the two must not disagree.
        val padded = "t".repeat(Limits.TITLE_MAX_LENGTH) + " "
        assertIs<ValidationResult.Invalid>(Validators.diaryTitle(padded))
    }

    @Test
    fun `content rejects blank but allows a single word`() {
        assertIs<ValidationResult.Invalid>(Validators.diaryContent("  \n\t "))
        assertIs<ValidationResult.Valid>(Validators.diaryContent("ok"))
    }

    @Test
    fun `tags are bounded by count and by individual length`() {
        assertIs<ValidationResult.Valid>(Validators.tags(List(20) { "tag$it" }))

        val tooMany = assertIs<ValidationResult.Invalid>(Validators.tags(List(21) { "tag$it" }))
        assertEquals(ErrorKeys.TOO_MANY_TAGS, tooMany.errors.errors.single().reasonKey)

        val tooLong = assertIs<ValidationResult.Invalid>(Validators.tags(listOf("t".repeat(51))))
        assertEquals(Field.TAG, tooLong.errors.errors.single().field)
    }

    @Test
    fun `emotions and notes are bounded`() {
        assertIs<ValidationResult.Invalid>(Validators.emotions(List(21) { "e$it" }))
        assertIs<ValidationResult.Invalid>(Validators.moodNote("n".repeat(Limits.MOOD_NOTE_MAX_LENGTH + 1)))
        assertIs<ValidationResult.Valid>(Validators.moodNote(null))
        assertIs<ValidationResult.Valid>(Validators.moodNote("short"))
    }

    @Test
    fun `an inverted date range is rejected and an equal range is accepted`() {
        assertIs<ValidationResult.Invalid>(Validators.dateRange(2_000L, 1_000L))
        assertIs<ValidationResult.Valid>(Validators.dateRange(1_000L, 1_000L))
        assertIs<ValidationResult.Valid>(Validators.dateRange(1_000L, 2_000L))
    }

    @Test
    fun `tag normalisation trims collapses lowercases and drops empties`() {
        assertEquals("quiet morning", Validators.normaliseTag("  Quiet   Morning "))
        assertEquals(null, Validators.normaliseTag("   "))
        assertEquals("t".repeat(Limits.TAG_MAX_LENGTH), Validators.normaliseTag("t".repeat(200)))
    }

    @Test
    fun `errors accumulate and can be looked up per field`() {
        val errors = ValidationErrors.None +
            ValidationError(Field.TITLE, ErrorKeys.VALIDATION_REQUIRED) +
            ValidationErrors(listOf(ValidationError(Field.EMAIL, ErrorKeys.INVALID_EMAIL)))

        assertEquals(2, errors.errors.size)
        assertTrue(errors.hasErrors)
        assertEquals(Field.EMAIL, errors.forField(Field.EMAIL)?.field)
        assertEquals(null, errors.forField(Field.PASSWORD))
        assertEquals(2, errors.toSet().size)
    }
}
