package com.givayo.futboltahmin

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.text.Normalizer
import java.util.Date
import java.util.Locale
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("futbol_tahmin", MODE_PRIVATE) }
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private val green = Color.rgb(17, 105, 76)
    private val ink = Color.rgb(28, 38, 48)
    private val muted = Color.rgb(98, 110, 120)
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildShell()
        showHome()
        if (prefs.getString("api_key", "").isNullOrBlank()) showApiKeyDialog(false)
    }

    private fun buildShell() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(245, 247, 249)) }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(22), dp(20), dp(16)); setBackgroundColor(green)
        }
        header.addView(TextView(this).apply { text = "FUTBOL TAHMİN"; textSize = 22f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD) })
        header.addView(TextView(this).apply { text = "İstatistik tabanlı maç analizi · Oran yok"; textSize = 13f; setTextColor(Color.rgb(220, 240, 231)); setPadding(0, dp(5), 0, 0) })
        root.addView(header)
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), dp(8), dp(12), dp(8)); setBackgroundColor(Color.WHITE) }
        nav.addView(navButton("Bugünün Maçları") { showHome() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        nav.addView(navButton("Tahmin Geçmişi") { showHistory() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        nav.addView(navButton("API Ayarı") { showApiKeyDialog(true) }, LinearLayout.LayoutParams(0, dp(44), 1f))
        root.addView(nav)
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(24)) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun navButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label; textSize = 11f; isAllCaps = false; setTextColor(green); setOnClickListener { action() }
    }

    private fun showHome() {
        content.removeAllViews()
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        content.addView(title("Bugünün maçları"))
        content.addView(body("Tarih: $date  ·  Veriler API-Football üzerinden alınır."))
        val refresh = Button(this).apply { text = if (loading) "Yükleniyor…" else "Maçları güncelle"; isAllCaps = false; setOnClickListener { loadFixtures(date) } }
        content.addView(refresh, matchWidth())
        if (prefs.getString("api_key", "").isNullOrBlank()) {
            content.addView(note("Başlamak için API Ayarı bölümüne API-Football anahtarınızı girin. API anahtarı olmadan canlı veriler alınamaz."))
        } else {
            content.addView(body("Tahminler son 5 maçın gol verilerinden hesaplanır. Örnek veya garanti sonuç değildir."))
            loadFixtures(date)
        }
    }

    private fun loadFixtures(date: String) {
        val key = prefs.getString("api_key", "") ?: ""
        if (key.isBlank()) { showApiKeyDialog(true); return }
        if (loading) return
        loading = true
        content.removeAllViews(); content.addView(title("Maçlar yükleniyor…")); content.addView(body("Fikstür ve takım istatistikleri alınıyor."))
        Thread {
            try {
                val json = apiGet("https://v3.football.api-sports.io/fixtures?date=$date&timezone=Europe%2FIstanbul", key)
                val response = json.optJSONArray("response") ?: JSONArray()
                runOnUiThread {
                    loading = false
                    content.removeAllViews()
                    content.addView(title("Bugünün maçları (${response.length()})"))
                    content.addView(body("Tarih: $date · Her maç için analiz isteği API kullanım limitlerine tabi olabilir."))
                    if (response.length() == 0) content.addView(note("Bu tarih için maç bulunamadı veya veri sağlayıcı sonuç döndürmedi."))
                    for (i in 0 until response.length()) {
                        val item = response.optJSONObject(i) ?: continue
                        addFixtureCard(item, key)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    loading = false; content.removeAllViews(); content.addView(title("Veri alınamadı"))
                    content.addView(note("${e.message ?: "Bağlantı hatası"}\n\nAPI anahtarınızı, internet bağlantınızı ve sağlayıcı kotanızı kontrol edin."))
                    content.addView(Button(this).apply { text = "Tekrar dene"; isAllCaps = false; setOnClickListener { loadFixtures(date) } })
                }
            }
        }.start()
    }

    private fun addFixtureCard(item: JSONObject, key: String) {
        val fixture = item.optJSONObject("fixture") ?: JSONObject()
        val teams = item.optJSONObject("teams") ?: JSONObject()
        val home = teams.optJSONObject("home") ?: JSONObject()
        val away = teams.optJSONObject("away") ?: JSONObject()
        val league = item.optJSONObject("league") ?: JSONObject()
        val homeId = home.optInt("id", 0); val awayId = away.optInt("id", 0)
        val homeName = home.optString("name", "Ev sahibi"); val awayName = away.optString("name", "Deplasman")
        val id = fixture.optInt("id", 0)
        val kickoff = fixture.optString("date", "").replace("T", " ").take(16)
        // API-Football fixture history requests work more reliably when the season is explicit.
        val season = league.optInt("season", kickoff.take(4).toIntOrNull() ?: 2026)
        val card = verticalCard()
        card.addView(TextView(this).apply { text = "${league.optString("name", "Lig")} · $kickoff"; textSize = 12f; setTextColor(muted) })
        card.addView(TextView(this).apply { text = "$homeName  -  $awayName"; textSize = 17f; setTextColor(ink); setTypeface(null, Typeface.BOLD); setPadding(0, dp(7), 0, dp(4)) })
        val status = fixture.optJSONObject("status")?.optString("short", "") ?: ""
        if (status == "FT" || status == "AET" || status == "PEN") {
            val goals = item.optJSONObject("goals") ?: JSONObject()
            card.addView(body("Sonuç: ${goals.opt("home")} - ${goals.opt("away")}"))
        }
        val result = TextView(this).apply { text = "Analiz için düğmeye basın. API kotasını korumak için istatistikler istek üzerine alınır."; textSize = 13f; setTextColor(muted); setPadding(0, dp(6), 0, dp(6)) }
        card.addView(result)
        val analyze = Button(this).apply { text = "İstatistikleri analiz et"; isAllCaps = false }
        card.addView(analyze, matchWidth())
        val save = Button(this).apply { text = "Tahmini geçmişe kaydet"; isAllCaps = false; isEnabled = false }
        card.addView(save, matchWidth())
        save.setOnClickListener {
            val text = result.text.toString()
            val predicted = Regex("Maç sonucu: ([1X2])").find(text)?.groupValues?.getOrNull(1) ?: "?"
            if (predicted == "?") { Toast.makeText(this, "Önce analiz yapın", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val entry = JSONObject().put("fixtureId", id).put("home", homeName).put("away", awayName)
                .put("league", league.optString("name", "Lig")).put("date", kickoff).put("prediction", predicted)
                .put("analysis", text.toString()).put("rated", false)
            val hist = readHistory(); hist.put(entry); prefs.edit().putString("history", hist.toString()).apply()
            save.text = "Kaydedildi"; save.isEnabled = false
            Toast.makeText(this, "Tahmin geçmişe kaydedildi", Toast.LENGTH_SHORT).show()
        }
        content.addView(card)
        analyze.setOnClickListener {
            if (homeId <= 0 || awayId <= 0) { result.text = "Takım kimlikleri alınamadı."; return@setOnClickListener }
            analyze.isEnabled = false; analyze.text = "Analiz ediliyor…"; result.text = "Geçmiş maç verileri alınıyor…"
            Thread {
                try {
                    val secondKey = prefs.getString("footballdata_key", "") ?: ""
                    var hs: Stats? = null
                    var as_: Stats? = null
                    var source = "API-Football"
                    var primaryProblem = ""
                    try {
                        val homeRecent = apiGet(
                            "https://v3.football.api-sports.io/fixtures?team=$homeId&last=20&season=$season&timezone=Europe%2FIstanbul",
                            key
                        ).optJSONArray("response") ?: JSONArray()
                        val awayRecent = apiGet(
                            "https://v3.football.api-sports.io/fixtures?team=$awayId&last=20&season=$season&timezone=Europe%2FIstanbul",
                            key
                        ).optJSONArray("response") ?: JSONArray()
                        hs = teamStats(homeRecent, homeId)
                        as_ = teamStats(awayRecent, awayId)
                        if (hs!!.count < 3 || as_!!.count < 3) primaryProblem = "API-Football en az 3 geçerli geçmiş maç sağlayamadı."
                    } catch (e: Exception) {
                        primaryProblem = e.message ?: "API-Football geçmiş maç isteği başarısız oldu."
                    }
                    if ((hs?.count ?: 0) < 3 || (as_?.count ?: 0) < 3) {
                        if (secondKey.isBlank()) {
                            throw IllegalStateException("API-Football bu sezon için geçmiş maç verisi vermedi. Hibrit analiz için API Ayarı bölümüne Footballdata.io API anahtarını da girin. Ayrıntı: $primaryProblem")
                        }
                        hs = footballdataTeamStats(homeName, secondKey)
                        as_ = footballdataTeamStats(awayName, secondKey)
                        source = "Footballdata.io (geçmiş sonuçlar)"
                    }
                    val homeStats = hs ?: emptyStats()
                    val awayStats = as_ ?: emptyStats()
                    if (homeStats.count < 3 || awayStats.count < 3) {
                        val message = "Güvenilir analiz için yeterli doğrulanmış geçmiş maç verisi bulunamadı.\nEv sahibi: ${homeStats.count} maç, deplasman: ${awayStats.count} maç.\nEn az 3 tamamlanmış maç gerekir. Bu durumda tahmin üretmek yerine veri kapsamını kontrol edin.\nKaynak ayrıntısı: $primaryProblem"
                        runOnUiThread { result.text = message; save.isEnabled = false; analyze.isEnabled = true; analyze.text = "Tekrar dene" }
                    } else {
                        val analysis = calculatePrediction(homeStats, awayStats) + "\nVeri kaynağı: $source"
                        runOnUiThread { result.text = analysis; save.isEnabled = true; analyze.isEnabled = true; analyze.text = "Yeniden analiz et" }
                    }
                } catch (e: Exception) {
                    runOnUiThread { result.text = "Analiz alınamadı: ${e.message ?: "API/bağlantı hatası"}"; analyze.isEnabled = true; analyze.text = "Tekrar dene" }
                }
            }.start()
        }
    }

    private data class Stats(
        val scored: Double, val conceded: Double, val count: Int, val form: String,
        val homeScored: Double, val homeConceded: Double, val homeCount: Int,
        val awayScored: Double, val awayConceded: Double, val awayCount: Int
    )

    private fun teamStats(matches: JSONArray, teamId: Int): Stats {
        val ordered = (0 until matches.length()).mapNotNull { matches.optJSONObject(it) }
            .sortedByDescending { it.optJSONObject("fixture")?.optString("date", "") ?: "" }
        var scored = 0.0; var conceded = 0.0; var count = 0
        var homeScored = 0.0; var homeConceded = 0.0; var homeCount = 0
        var awayScored = 0.0; var awayConceded = 0.0; var awayCount = 0
        val form = StringBuilder()
        for (m in ordered) {
            val fixture = m.optJSONObject("fixture") ?: continue
            val status = fixture.optJSONObject("status")?.optString("short", "") ?: ""
            if (status !in setOf("FT", "AET", "PEN")) continue
            val teams = m.optJSONObject("teams") ?: continue
            val goals = m.optJSONObject("goals") ?: continue
            val h = teams.optJSONObject("home") ?: continue
            val a = teams.optJSONObject("away") ?: continue
            val homeId = h.optInt("id", -1); val awayId = a.optInt("id", -1)
            if (homeId != teamId && awayId != teamId) continue
            val hg = goals.optInt("home", -1); val ag = goals.optInt("away", -1)
            if (hg < 0 || ag < 0) continue
            val isHome = homeId == teamId
            val forGoals = if (isHome) hg else ag
            val against = if (isHome) ag else hg
            scored += forGoals; conceded += against; count++
            if (isHome) { homeScored += forGoals; homeConceded += against; homeCount++ }
            else { awayScored += forGoals; awayConceded += against; awayCount++ }
            form.append(if (forGoals > against) "G" else if (forGoals == against) "B" else "M").append(" ")
            if (count >= 10) break
        }
        if (count == 0) return emptyStats()
        return Stats(scored / count, conceded / count, count, form.toString().trim().split(Regex("\\s+")).asReversed().joinToString(" "),
            if (homeCount > 0) homeScored / homeCount else scored / count,
            if (homeCount > 0) homeConceded / homeCount else conceded / count, homeCount,
            if (awayCount > 0) awayScored / awayCount else scored / count,
            if (awayCount > 0) awayConceded / awayCount else conceded / count, awayCount)
    }

    private fun emptyStats() = Stats(0.0, 0.0, 0, "Veri yok", 0.0, 0.0, 0, 0.0, 0.0, 0)

    private fun calculatePrediction(home: Stats, away: Stats): String {
        // Prefer home team's home record and away team's away record when at least two samples exist.
        val homeAttack = if (home.homeCount >= 2) home.homeScored else home.scored
        val homeDefense = if (home.homeCount >= 2) home.homeConceded else home.conceded
        val awayAttack = if (away.awayCount >= 2) away.awayScored else away.scored
        val awayDefense = if (away.awayCount >= 2) away.awayConceded else away.conceded
        val lh = (homeAttack * 0.55 + awayDefense * 0.45 + 0.12).coerceIn(0.15, 3.8)
        val la = (awayAttack * 0.55 + homeDefense * 0.45 - 0.05).coerceIn(0.15, 3.8)
        val hp = DoubleArray(9) { poisson(lh, it) }; val ap = DoubleArray(9) { poisson(la, it) }
        var p1 = 0.0; var px = 0.0; var p2 = 0.0; var over = 0.0; var btts = 0.0; var best = 0.0; var score = "0-0"
        for (h in 0..8) for (a in 0..8) {
            val p = hp[h] * ap[a]
            if (h > a) p1 += p else if (h == a) px += p else p2 += p
            if (h + a >= 3) over += p
            if (h > 0 && a > 0) btts += p
            if (p > best) { best = p; score = "$h-$a" }
        }
        // Normalize outcome probabilities so 1/X/2 add up to 100% despite finite score range.
        val total = (p1 + px + p2).coerceAtLeast(0.000001)
        p1 /= total; px /= total; p2 /= total
        fun pct(v: Double) = "%.0f%%".format(Locale.US, v.coerceIn(0.0, 1.0) * 100)
        val sampleNote = if (home.count < 5 || away.count < 5) "\nUyarı: En az bir takımda 5'ten az maç var; tahmin güveni düşüktür." else ""
        val venueNote = "\nEv sahibinin iç saha örneği: ${home.homeCount} maç; deplasmanın dış saha örneği: ${away.awayCount} maç."
        return "Maç sonucu: ${if (p1 >= px && p1 >= p2) "1" else if (p2 >= p1 && p2 >= px) "2" else "X"}\n1: ${pct(p1)}   X: ${pct(px)}   2: ${pct(p2)}\n2,5 üst: ${pct(over)}   Karşılıklı gol: ${pct(btts)}\nEn olası skor: $score\nSon ${home.count} maç ortalaması — Ev sahibi: ${"%.2f".format(Locale.US, home.scored)} atılan / ${"%.2f".format(Locale.US, home.conceded)} yenilen\nSon ${away.count} maç ortalaması — Deplasman: ${"%.2f".format(Locale.US, away.scored)} atılan / ${"%.2f".format(Locale.US, away.conceded)} yenilen\nForm (G: galibiyet, B: beraberlik, M: mağlubiyet): ${home.form} | ${away.form}$venueNote$sampleNote\nModel: son tamamlanmış maçların skorlarından üretilen basit Poisson tahmini; kesinlik veya garanti içermez."
    }

    private fun poisson(lambda: Double, k: Int): Double {
        var factorial = 1.0; for (i in 2..k) factorial *= i.toDouble()
        return exp(-lambda) * Math.pow(lambda, k.toDouble()) / factorial
    }

    private fun showHistory() {
        content.removeAllViews(); content.addView(title("Tahmin geçmişi"))
        val hist = readHistory()
        var rated = 0; var correct = 0
        for (i in 0 until hist.length()) { val e = hist.optJSONObject(i) ?: continue; if (e.optBoolean("rated")) { rated++; if (e.optBoolean("correct")) correct++ } }
        val rate = if (rated == 0) "Henüz değerlendirilmiş tahmin yok" else "Değerlendirilen: $rated · Doğru: $correct · Başarı: ${"%.1f".format(Locale.US, correct * 100.0 / rated)}%"
        content.addView(note(rate))
        content.addView(body("Tahminleri maç kartındaki düğmeyle kaydedin. Maç bittikten sonra sonucu el ile değerlendirerek başarı takibi yapabilirsiniz."))
        if (hist.length() == 0) content.addView(note("Henüz kaydedilmiş tahmin bulunmuyor."))
        for (i in hist.length() - 1 downTo 0) {
            val e = hist.optJSONObject(i) ?: continue
            val card = verticalCard()
            card.addView(TextView(this).apply { text = "${e.optString("home")} - ${e.optString("away")}"; textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(ink) })
            card.addView(body("${e.optString("league")} · ${e.optString("date")}\nTahmin: ${e.optString("prediction")}"))
            if (e.optBoolean("rated")) card.addView(body(if (e.optBoolean("correct")) "Değerlendirme: Doğru" else "Değerlendirme: Yanlış"))
            else card.addView(Button(this).apply {
                text = "Sonuç tahminimi değerlendir"; isAllCaps = false
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity).setTitle("Tahmin doğru muydu?")
                        .setItems(arrayOf("Doğru", "Yanlış", "İptal")) { _, which ->
                            if (which < 2) { e.put("rated", true).put("correct", which == 0); prefs.edit().putString("history", hist.toString()).apply(); showHistory() }
                        }.show()
                }
            })
            content.addView(card)
        }
        if (hist.length() > 0) content.addView(Button(this).apply { text = "Geçmişi temizle"; isAllCaps = false; setOnClickListener { AlertDialog.Builder(this@MainActivity).setMessage("Tüm tahmin geçmişi silinsin mi?").setPositiveButton("Sil") { _, _ -> prefs.edit().remove("history").apply(); showHistory() }.setNegativeButton("Vazgeç", null).show() } })
    }

    private fun showApiKeyDialog(allowCancel: Boolean) {
        val input = EditText(this).apply { hint = "API-Football anahtarı (gerekli)"; setSingleLine(true); setText(prefs.getString("api_key", "")) }
        val secondInput = EditText(this).apply { hint = "Footballdata.io anahtarı (isteğe bağlı)"; setSingleLine(true); setText(prefs.getString("footballdata_key", "")) }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(8), dp(22), 0) }
        box.addView(TextView(this).apply { text = "API-Football güncel maç listesini sağlar. Geçmiş sezon verisi kısıtlanırsa Footballdata.io anahtarı analiz için yedek kaynak olarak kullanılır. Anahtarlar yalnızca bu cihazda saklanır."; textSize = 14f; setTextColor(ink) })
        box.addView(TextView(this).apply { text = "API-Football"; textSize = 13f; setTextColor(ink); setPadding(0, dp(12), 0, 0) })
        box.addView(input)
        box.addView(TextView(this).apply { text = "Footballdata.io (yedek geçmiş maç kaynağı)"; textSize = 13f; setTextColor(ink); setPadding(0, dp(10), 0, 0) })
        box.addView(secondInput)
        val builder = AlertDialog.Builder(this).setTitle("Veri kaynağı ayarı").setView(box)
            .setPositiveButton("Kaydet", null)
        if (allowCancel) builder.setNegativeButton("İptal", null)
        val dialog = builder.create(); dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val key = input.text.toString().trim()
                if (key.isBlank()) { input.error = "API-Football anahtarı gerekli"; return@setOnClickListener }
                prefs.edit()
                    .putString("api_key", key)
                    .putString("footballdata_key", secondInput.text.toString().trim())
                    .apply()
                dialog.dismiss(); showHome()
            }
        }; dialog.show()
    }

    private fun footballdataGet(url: String, key: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"; conn.connectTimeout = 15000; conn.readTimeout = 20000
        conn.setRequestProperty("Authorization", "Bearer $key"); conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(text)
            if (code !in 200..299 || json.optBoolean("success", true).not()) {
                val detail = json.optString("message", json.optString("error", text.take(250)))
                throw IllegalStateException("Footballdata.io HTTP $code: $detail")
            }
            return json
        } finally { conn.disconnect() }
    }

    private fun footballdataArray(json: JSONObject): JSONArray {
        // API yanıtları bazı endpointlerde data: [], bazılarında data: {matches: []}
        // ve bazı sürümlerde data.results.* biçiminde gelebiliyor. İç içe yapıları da tara.
        json.optJSONArray("data")?.let { return it }
        json.optJSONArray("response")?.let { return it }
        val data = json.optJSONObject("data")
        val root = data ?: json
        val preferredKeys = listOf("matches", "teams", "results", "fixtures", "items")
        for (key in preferredKeys) root.optJSONArray(key)?.let { return it }
        for (key in preferredKeys) {
            val child = root.optJSONObject(key)
            if (child != null) {
                for (nestedKey in preferredKeys) child.optJSONArray(nestedKey)?.let { return it }
            }
        }
        // Bazı cevaplarda data.results bir nesne, onun içinde teams/matches bulunur.
        val resultsObject = root.optJSONObject("results")
        if (resultsObject != null) {
            for (key in preferredKeys) resultsObject.optJSONArray(key)?.let { return it }
        }
        return JSONArray()
    }

    private fun cleanTeamName(name: String): String {
        val ascii = Normalizer.normalize(name.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace("ı", "i")
        return ascii.replace(Regex("[^a-z0-9]"), "")
            .replace("footballclub", "").replace("club", "").replace("fc", "")
            .replace("afc", "").replace("cf", "")
    }

    private fun footballdataTeamStats(teamName: String, key: String): Stats {
        val encoded = URLEncoder.encode(teamName, "UTF-8")
        val search = footballdataGet("https://footballdata.io/api/v1/teams?q=$encoded&limit=20", key)
        val candidates = footballdataArray(search)
        val target = cleanTeamName(teamName)
        var best: JSONObject? = null
        var bestScore = 0
        val exactIds = mutableSetOf<Int>()
        for (i in 0 until candidates.length()) {
            val team = candidates.optJSONObject(i) ?: continue
            val names = listOf("team_name", "team_name_clean", "team_name_english", "full_name", "name", "short_name")
                .map { team.optString(it, "") }.filter { it.isNotBlank() && it != "null" }
            val score = names.maxOfOrNull { candidateName ->
                val candidate = cleanTeamName(candidateName)
                when {
                    candidate == target -> 100
                    candidate.length >= 6 && target.length >= 6 && (candidate.startsWith(target) || target.startsWith(candidate)) -> 70
                    else -> 0
                }
            } ?: 0
            val id = team.optInt("team_id", team.optInt("id", 0))
            if (score == 100 && id > 0) exactIds.add(id)
            if (score > bestScore) { bestScore = score; best = team }
        }
        val team = best ?: throw IllegalStateException("Footballdata.io içinde '$teamName' takımı bulunamadı. Lig kapsamını kontrol edin.")
        if (bestScore < 100) throw IllegalStateException("Footballdata.io takım eşleşmesi yeterince kesin değil: $teamName. Yanlış takımın formu kullanılmadı.")
        if (exactIds.size > 1) throw IllegalStateException("Footballdata.io içinde '$teamName' adıyla birden fazla takım bulundu; yanlış takımı seçmemek için analiz durduruldu.")
        val publicId = team.optInt("team_id", team.optInt("id", 0))
        if (publicId <= 0) throw IllegalStateException("Footballdata.io takım kimliği alınamadı: $teamName")

        val matchesJson = footballdataGet("https://footballdata.io/api/v1/teams/$publicId/matches?limit=100", key)
        val matches = footballdataArray(matchesJson)
        fun nestedScore(score: JSONObject, directKey: String, root: JSONObject): Int {
            val direct = score.opt(directKey)
            if (direct is Number) return direct.toInt()
            val variants = listOf("full_time", "fullTime", "ft", "final", "regular_time")
            for (variant in variants) {
                val obj = score.optJSONObject(variant) ?: continue
                val v = obj.opt(directKey)
                if (v is Number) return v.toInt()
            }
            val rootKey = if (directKey == "home") "home_score" else "away_score"
            val rootValue = root.opt(rootKey)
            return if (rootValue is Number) rootValue.toInt() else -1
        }
        fun teamObj(match: JSONObject, side: String): JSONObject =
            match.optJSONObject("${side}_team") ?: match.optJSONObject(side) ?: JSONObject()
        fun matchDateKey(match: JSONObject): Long {
            val unix = match.optLong("date_unix", 0L)
            if (unix > 0L) return unix
            val raw = match.optString("match_date", match.optString("date", match.optString("kickoff", "")))
            return try {
                // ISO date/time sorts reliably; parsing also handles dates with a time suffix.
                val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { isLenient = false }
                val normalized = raw.replace("Z", "").substringBefore('.')
                (fmt.parse(normalized)?.time ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(raw.take(10))?.time ?: 0L) / 1000L
            } catch (_: Exception) { 0L }
        }
        val ordered = (0 until matches.length()).mapNotNull { matches.optJSONObject(it) }
            .distinctBy { it.optString("match_id", it.optString("id", "")) + "|" + it.optString("match_date", it.optString("date", "")) }
            .sortedByDescending { matchDateKey(it) }

        var scored = 0.0; var conceded = 0.0; var count = 0
        var homeScored = 0.0; var homeConceded = 0.0; var homeCount = 0
        var awayScored = 0.0; var awayConceded = 0.0; var awayCount = 0
        val form = StringBuilder()
        for (match in ordered) {
            val status = match.optString("status", match.optString("status_short", match.optString("match_status", "")))
                .lowercase(Locale.ROOT).replace(Regex("[^a-z]"), "")
            if (status !in setOf("complete", "completed", "finished", "ft", "fulltime", "aet", "pen", "afterextratime", "afterpenalties", "final")) continue
            val home = teamObj(match, "home")
            val away = teamObj(match, "away")
            val score = match.optJSONObject("score") ?: match.optJSONObject("scores") ?: JSONObject()
            val homeGoals = nestedScore(score, "home", match)
            val awayGoals = nestedScore(score, "away", match)
            if (homeGoals < 0 || awayGoals < 0) continue

            val homeId = home.optInt("team_id", home.optInt("id", -1))
            val awayId = away.optInt("team_id", away.optInt("id", -1))
            val isHome = when {
                homeId == publicId -> true
                awayId == publicId -> false
                homeId > 0 || awayId > 0 -> continue
                cleanTeamName(home.optString("team_name", home.optString("name", ""))) == target -> true
                cleanTeamName(away.optString("team_name", away.optString("name", ""))) == target -> false
                else -> continue
            }
            // Do not use rows without a date: their place in the last-ten sequence is unknown.
            if (matchDateKey(match) <= 0L) continue
            val forGoals = if (isHome) homeGoals else awayGoals
            val against = if (isHome) awayGoals else homeGoals
            scored += forGoals; conceded += against; count++
            if (isHome) { homeScored += forGoals; homeConceded += against; homeCount++ }
            else { awayScored += forGoals; awayConceded += against; awayCount++ }
            // Order is newest to oldest, matching the selection order of the last completed matches.
            form.append(if (forGoals > against) "G" else if (forGoals == against) "B" else "M").append(" ")
            if (count >= 10) break
        }
        if (count == 0) throw IllegalStateException("Footballdata.io '$teamName' için tarihli, skoru doğrulanmış tamamlanmış maç bulamadı. Güvenilir olmayan form üretilmedi.")
        return Stats(scored / count, conceded / count, count, form.toString().trim().split(Regex("\\s+")).asReversed().joinToString(" "),
            if (homeCount > 0) homeScored / homeCount else scored / count,
            if (homeCount > 0) homeConceded / homeCount else conceded / count, homeCount,
            if (awayCount > 0) awayScored / awayCount else scored / count,
            if (awayCount > 0) awayConceded / awayCount else conceded / count, awayCount)
    }

    private fun apiGet(url: String, key: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"; conn.connectTimeout = 15000; conn.readTimeout = 20000
        conn.setRequestProperty("x-apisports-key", key); conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(text)
            if (code !in 200..299) {
                val errors = json.optJSONObject("errors")?.toString() ?: text.take(300)
                throw IllegalStateException("HTTP $code: $errors")
            }
            val errors = json.optJSONObject("errors")
            if (errors != null && errors.length() > 0) throw IllegalStateException("API: $errors")
            return json
        } finally { conn.disconnect() }
    }

    private fun readHistory(): JSONArray = try { JSONArray(prefs.getString("history", "[]")) } catch (_: Exception) { JSONArray() }
    private fun title(s: String) = TextView(this).apply { text = s; textSize = 21f; setTextColor(ink); setTypeface(null, Typeface.BOLD); setPadding(0, 0, 0, dp(7)) }
    private fun body(s: String) = TextView(this).apply { text = s; textSize = 13f; setTextColor(muted); setPadding(0, dp(4), 0, dp(6)) }
    private fun note(s: String) = TextView(this).apply { text = s; textSize = 14f; setTextColor(ink); setPadding(dp(14), dp(12), dp(14), dp(12)); setBackgroundColor(Color.rgb(231, 241, 236)); setLineSpacing(dp(2).toFloat(), 1f) }
    private fun verticalCard() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)); setBackgroundColor(Color.WHITE); elevation = dp(2).toFloat(); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) } }
    private fun matchWidth() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
