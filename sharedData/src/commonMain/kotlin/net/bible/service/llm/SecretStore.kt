package net.bible.service.llm

import net.bible.android.database.IdType

fun apiKeyName(providerConfigId: IdType): String = "llm_api_key_$providerConfigId"
