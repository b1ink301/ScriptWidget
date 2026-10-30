plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.spotless)
}

// ktlint in IntelliJ style. The codebase keeps compact one-line signatures, expression bodies and
// call chains, so the layout rules that would rewrite most files are off; what remains catches
// spacing, indentation-free formatting slips, unused imports and similar.
val ktlintRules = mapOf(
    "ktlint_code_style" to "intellij_idea",
    "max_line_length" to "off",
    "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
    "ktlint_standard_multiline-expression-wrapping" to "disabled",
    "ktlint_standard_chain-method-continuation" to "disabled",
    "ktlint_standard_function-signature" to "disabled",
    "ktlint_standard_class-signature" to "disabled",
    "ktlint_standard_function-expression-body" to "disabled",
    "ktlint_standard_string-template-indent" to "disabled",
    "ktlint_standard_property-wrapping" to "disabled",
    "ktlint_standard_parameter-list-wrapping" to "disabled",
    "ktlint_standard_argument-list-wrapping" to "disabled",
    "ktlint_standard_wrapping" to "disabled",
    "ktlint_standard_statement-wrapping" to "disabled",
    "ktlint_standard_if-else-wrapping" to "disabled",
    "ktlint_standard_if-else-bracing" to "disabled",
    "ktlint_standard_trailing-comma-on-call-site" to "disabled",
    "ktlint_standard_trailing-comma-on-declaration-site" to "disabled",
    "ktlint_standard_blank-line-before-declaration" to "disabled",
    "ktlint_standard_import-ordering" to "disabled",
    "ktlint_standard_no-wildcard-imports" to "disabled",
    "ktlint_standard_kdoc" to "disabled",
    "ktlint_standard_multiline-if-else" to "disabled",
    "ktlint_standard_multiline-loop" to "disabled",
    "ktlint_standard_blank-line-between-when-conditions" to "disabled",
    "ktlint_standard_property-naming" to "disabled",
    "ktlint_standard_mixed-condition-operators" to "disabled",
    "ktlint_standard_backing-property-naming" to "disabled",
    "ktlint_standard_function-literal" to "disabled",
    "ktlint_standard_condition-wrapping" to "disabled",
    "ktlint_standard_binary-expression-wrapping" to "disabled",
    "ktlint_standard_chain-wrapping" to "disabled",
    "ktlint_standard_indent" to "disabled",
    "ktlint_standard_annotation" to "disabled",
    "ktlint_standard_spacing-between-declarations-with-annotations" to "disabled",
    "ktlint_standard_spacing-between-declarations-with-comments" to "disabled",
    "ktlint_standard_comment-wrapping" to "disabled",
    "ktlint_standard_discouraged-comment-location" to "disabled",
    "ktlint_standard_value-argument-comment" to "disabled",
    "ktlint_standard_value-parameter-comment" to "disabled",
    "ktlint_standard_type-argument-comment" to "disabled",
    "ktlint_standard_enum-wrapping" to "disabled",
    "ktlint_standard_try-catch-finally-spacing" to "disabled",
)

// Code style for every module: `./gradlew spotlessCheck` (CI) / `./gradlew spotlessApply` (fix).
spotless {
    kotlin {
        target("*/src/**/*.kt")
        ktlint().editorConfigOverride(ktlintRules)
    }
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts")
        ktlint().editorConfigOverride(ktlintRules)
    }
}
