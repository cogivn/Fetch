package com.tonyodev.fetch2core

import com.google.gson.FieldAttributes

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
annotation class Exclude


class ExclusionStrategy : com.google.gson.ExclusionStrategy {
    override fun shouldSkipField(f: FieldAttributes?): Boolean =
        f?.getAnnotation(Exclude::class.java) != null

    override fun shouldSkipClass(clazz: Class<*>?): Boolean = false
}