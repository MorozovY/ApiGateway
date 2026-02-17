package com.company.gateway.admin.dto

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

/**
 * Аннотация для валидации списка HTTP методов.
 *
 * Проверяет, что все элементы списка являются допустимыми HTTP методами:
 * GET, POST, PUT, DELETE, PATCH.
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [HttpMethodsValidator::class])
@MustBeDocumented
annotation class ValidHttpMethods(
    val message: String = "Метод должен быть одним из: GET, POST, PUT, DELETE, PATCH",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

/**
 * Валидатор для списка HTTP методов.
 *
 * Проверяет каждый элемент списка на соответствие допустимым HTTP методам.
 */
class HttpMethodsValidator : ConstraintValidator<ValidHttpMethods, List<String>?> {

    companion object {
        private val ALLOWED_METHODS = setOf("GET", "POST", "PUT", "DELETE", "PATCH")
    }

    override fun isValid(value: List<String>?, context: ConstraintValidatorContext): Boolean {
        // null значения пропускаем — они обрабатываются @NotEmpty
        if (value == null) return true

        // Проверяем каждый метод
        val invalidMethods = value.filter { it !in ALLOWED_METHODS }
        if (invalidMethods.isNotEmpty()) {
            // Формируем сообщение с перечислением невалидных методов
            context.disableDefaultConstraintViolation()
            context.buildConstraintViolationWithTemplate(
                "Недопустимые HTTP методы: ${invalidMethods.joinToString(", ")}. " +
                "Допустимые значения: GET, POST, PUT, DELETE, PATCH"
            ).addConstraintViolation()
            return false
        }

        return true
    }
}
