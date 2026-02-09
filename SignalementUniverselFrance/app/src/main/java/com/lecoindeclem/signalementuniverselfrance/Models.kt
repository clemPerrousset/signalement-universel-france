package com.lecoindeclem.signalementuniverselfrance

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

class MairieViewModel : ViewModel() {

    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        defaultRequest {
            headers.append("User-Agent", "SignalementUniverselFrance/1.0 (Android)")
        }
    }

    private val _mairieEmail = MutableStateFlow<String?>(null)
    val mairieEmail: StateFlow<String?> = _mairieEmail

    private val _mairieLabel = MutableStateFlow<String?>(null)
    val mairieLabel: StateFlow<String?> = _mairieLabel

    private val _mairieAdresse = MutableStateFlow<String?>(null)
    val mairieAdresse: StateFlow<String?> = _mairieAdresse

    fun fetchMairieFromLatLon(lat: Double, lon: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            clearMairie()
            try {
                val insee = getCommuneInseeFromLatLon(lat, lon)
                Log.d(TAG, "INSEE from geo.api.gouv.fr => $insee (lat=$lat, lon=$lon)")
                if (insee.isNullOrBlank()) {
                    Log.w(TAG, "Aucun code INSEE trouvé")
                    return@launch
                }

                val mairieEP = getMairieFromInseeViaEP(insee)
                if (mairieEP != null) {
                    Log.d(TAG, "Mairie via EP v3: ${mairieEP.nom} | ${mairieEP.email ?: "no-email"}")
                    pushMairie(mairieEP)
                    return@launch
                } else {
                    Log.w(TAG, "EP v3 n'a rien renvoyé pour $insee, on tente l'annuaire SP…")
                }

                val mairieSP = getMairieFromInseeViaAnnuaireSP(insee)
                if (mairieSP != null) {
                    Log.d(TAG, "Mairie via Annuaire SP: ${mairieSP.nom} | ${mairieSP.email ?: "no-email"}")
                    pushMairie(mairieSP)
                } else {
                    Log.e(TAG, "Aucune mairie trouvée (EP v3 & SP) pour $insee")
                    clearMairie()
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchMairieFromLatLon failed", e)
                clearMairie()
            }
        }
    }

    private suspend fun pushMairie(m: MairieEntry) = withContext(Dispatchers.Main) {
        _mairieEmail.value = m.email
        _mairieLabel.value = m.nom
        _mairieAdresse.value = m.adresse
    }

    private fun clearMairie() {
        _mairieEmail.value = null
        _mairieLabel.value = null
        _mairieAdresse.value = null
    }

    private suspend fun getCommuneInseeFromLatLon(lat: Double, lon: Double): String? {
        val url = "https://geo.api.gouv.fr/communes"
        return withTimeout(12_000) {
            val text = client.get(url) {
                parameter("lat", lat)
                parameter("lon", lon)
                parameter("fields", "nom,code")
            }.bodyAsText()

            val root = Json.parseToJsonElement(text)
            val arr = root as? JsonArray ?: return@withTimeout null
            val first = arr.firstOrNull() as? JsonObject ?: return@withTimeout null
            first["code"]?.jsonPrimitive?.contentOrNull
        }
    }

    private suspend fun getMairieFromInseeViaEP(insee: String): MairieEntry? {
        val epUrl = Url("https://etablissements-publics.api.gouv.fr/v3/communes/$insee/mairie")
        return withTimeout(12_000) {
            val text = client.get(epUrl).bodyAsText()
            Log.v(TAG, "EP v3 raw: ${text.take(300)}…")

            val root = Json.parseToJsonElement(text) as? JsonObject ?: return@withTimeout null
            val features = root["features"] as? JsonArray ?: return@withTimeout null
            if (features.isEmpty()) return@withTimeout null

            for (f in features) {
                val fo = f as? JsonObject ?: continue
                val props = fo["properties"] as? JsonObject ?: continue

                val nom = props.optString("nom") ?: "Mairie"
                val email = props.optArray("mails")?.firstStringOrNull()
                    ?: props.optString("email")

                val adrs = props.optArray("adresses")
                val adresse = adrs?.firstObjectOrNull()?.let { ao ->
                    val lignes = (ao.optArray("lignes")?.joinToSingleLine()) ?: ""
                    val cp = ao.optString("codePostal") ?: ""
                    val com = ao.optString("commune") ?: ""
                    buildString {
                        if (lignes.isNotBlank()) append(lignes)
                        if (cp.isNotBlank() || com.isNotBlank()) {
                            if (isNotEmpty()) append(", ")
                            append(listOf(cp, com).filter { it.isNotBlank() }.joinToString(" "))
                        }
                    }.ifBlank { null }
                }

                return@withTimeout MairieEntry(nom = nom, email = email, adresse = adresse)
            }
            null
        }
    }

    private suspend fun getMairieFromInseeViaAnnuaireSP(insee: String): MairieEntry? {
        val url = "https://api-lannuaire.service-public.fr/api/records/1.0/search/"
        return withTimeout(12_000) {
            val body: String = client.get(url) {
                parameter("dataset", "api-lannuaire-administration")
                parameter("rows", "50")
                parameter("refine.code_insee_commune", insee)
                parameter("q", "mairie")
            }.bodyAsText()

            Log.v(TAG, "SP raw: ${body.take(300)}…")

            val root = Json.parseToJsonElement(body) as? JsonObject ?: return@withTimeout null
            val records = root["records"] as? JsonArray ?: return@withTimeout null

            for (el in records) {
                val fields = (el as? JsonObject)?.get("fields") as? JsonObject ?: continue
                val nom = fields.optString("nom_organisme")
                    ?: fields.optString("nom_entite")
                    ?: fields.optString("nom")
                    ?: continue

                if (!nom.contains("mairie", ignoreCase = true)) continue

                val email = fields.optString("courriel")
                    ?: fields.optString("email")
                    ?: fields.optArray("mails")?.firstStringOrNull()

                val a = fields.optString("adresse")
                val cp = fields.optString("code_postal")
                val com = fields.optString("commune")
                val adresse = buildString {
                    if (!a.isNullOrBlank()) append(a)
                    if (!cp.isNullOrBlank() || !com.isNullOrBlank()) {
                        if (isNotEmpty()) append(", ")
                        if (!cp.isNullOrBlank()) append(cp).append(" ")
                        if (!com.isNullOrBlank()) append(com)
                    }
                }.ifBlank { null }

                return@withTimeout MairieEntry(nom = nom, email = email, adresse = adresse)
            }
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        client.close()
    }

    companion object {
        private const val TAG = "MairieVM"
    }
}

@Serializable
data class GeoCommune(
    val code: String,
    val nom: String
)

data class MairieEntry(
    val nom: String,
    val email: String?,
    val adresse: String?
)


private fun JsonObject.optString(key: String): String? {
    val el = this[key] ?: return null
    (el as? JsonPrimitive)?.let {
        return try { it.content } catch (_: Exception) { null }
    }
    return try { el.jsonPrimitive.content } catch (_: Exception) { null }
}

private fun JsonObject.optArray(key: String): JsonArray? =
    this[key] as? JsonArray

private fun JsonArray.firstStringOrNull(): String? {
    for (e in this) {
        val p = e as? JsonPrimitive ?: continue
        try {
            val v = p.content
            if (v.isNotBlank()) return v
        } catch (_: Exception) { /* ignore */ }
    }
    return null
}

private fun JsonArray.firstObjectOrNull(): JsonObject? =
    this.firstOrNull { it is JsonObject } as? JsonObject

private fun JsonArray.joinToSingleLine(): String? =
    buildString {
        this@joinToSingleLine.forEach { e ->
            val s = (e as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (s.isNotEmpty()) {
                if (isNotEmpty()) append(" ")
                append(s)
            }
        }
    }.ifBlank { null }

private val JsonPrimitive.contentOrNull: String?
    get() = try { content } catch (_: Exception) { null }
