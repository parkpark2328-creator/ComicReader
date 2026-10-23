package com.example.comicreader

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.util.Locale

data class ReadingEntry(val url: String, val title: String, val cover: String, val lastIndex: Int)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ReaderScreen(this) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScreen(context: Context) {
    val prefs = remember { context.getSharedPreferences("comic_reader", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var urlInput by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf("") }
    var imageUrls by remember { mutableStateOf(emptyList<String>()) }
    var coverUrl by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("이미지 URL 또는 접근 권한이 있는 웹페이지 주소를 입력하세요.") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var history by remember { mutableStateOf(loadHistory(prefs)) }
    var favorites by remember { mutableStateOf(loadFavorites(prefs)) }
    var currentIndex by remember { mutableIntStateOf(0) }

    fun persistHistory() {
        if (currentUrl.isBlank()) return
        val updated = (listOf(ReadingEntry(currentUrl, title, coverUrl, currentIndex)) + history.filterNot { it.url == currentUrl }).take(100)
        history = updated
        prefs.edit().putString("history", historyJson(updated)).apply()
    }

    fun openPage(raw: String, resumeAt: Int = 0) {
        val target = raw.trim().let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
        loading = true
        status = "불러오는 중…"
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { extractImages(target) }
                currentUrl = target
                title = result.title
                coverUrl = result.cover
                imageUrls = result.images
                currentIndex = resumeAt.coerceIn(0, (result.images.size - 1).coerceAtLeast(0))
                status = if (result.images.isEmpty()) "이미지를 찾지 못했습니다. 이미지 직접 URL을 입력해 보세요." else "${result.images.size}개 이미지"
                if (result.images.isNotEmpty()) persistHistory()
            } catch (e: Exception) {
                status = "불러오기 실패: ${e.message ?: "주소 또는 네트워크를 확인해 주세요."}"
            } finally { loading = false }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Comic Reader") }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.Default.Search, null) }, label = { Text("읽기") })
                NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Icon(Icons.Default.History, null) }, label = { Text("최근") })
                NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.Default.Favorite, null) }, label = { Text("즐겨찾기") })
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> Column(Modifier.padding(padding).fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = urlInput, onValueChange = { urlInput = it }, modifier = Modifier.weight(1f),
                        label = { Text("웹페이지 / 이미지 URL") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { if (urlInput.isNotBlank()) openPage(urlInput) }, enabled = !loading) { Text("열기") }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(status, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
                if (coverUrl.isNotBlank()) {
                    AsyncImage(model = coverUrl, contentDescription = "표지", modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp), contentScale = ContentScale.Fit)
                }
                if (imageUrls.isNotEmpty()) {
                    Text(title, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.titleMedium)
                    LazyColumn(contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(imageUrls) { image ->
                            AsyncImage(model = image, contentDescription = "페이지 이미지", modifier = Modifier.fillMaxWidth().clickable {
                                currentIndex = imageUrls.indexOf(image)
                                persistHistory()
                            }, contentScale = ContentScale.FillWidth)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("${imageUrls.indexOf(image) + 1} / ${imageUrls.size}", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                                IconButton(onClick = {
                                    favorites = if (image in favorites) favorites - image else favorites + image
                                    prefs.edit().putString("favorites", JSONArray(favorites).toString()).apply()
                                }) { Icon(if (image in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "즐겨찾기") }
                            }
                        }
                    }
                }
            }
            1 -> LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                items(history) { entry ->
                    Row(Modifier.fillMaxWidth().clickable { urlInput = entry.url; selectedTab = 0; openPage(entry.url, entry.lastIndex) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (entry.cover.isNotBlank()) AsyncImage(entry.cover, null, Modifier.size(64.dp, 84.dp), contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(entry.title.ifBlank { entry.url }, style = MaterialTheme.typography.titleSmall)
                            Text("저장 위치: ${entry.lastIndex + 1}", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { history = history.filterNot { it.url == entry.url }; prefs.edit().putString("history", historyJson(history)).apply() }) { Text("삭제") }
                    }
                    HorizontalDivider()
                }
            }
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                items(favorites) { favorite ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(favorite, null, Modifier.size(72.dp, 96.dp), contentScale = ContentScale.Crop)
                        Text(favorite, Modifier.weight(1f).padding(8.dp), maxLines = 2, style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { favorites = favorites - favorite; prefs.edit().putString("favorites", JSONArray(favorites).toString()).apply() }) { Icon(Icons.Default.Favorite, null) }
                    }
                }
            }
        }
    }
}

private data class ExtractedPage(val title: String, val cover: String, val images: List<String>)

private fun extractImages(address: String): ExtractedPage {
    val clean = address.substringBefore('?').lowercase(Locale.ROOT)
    if (clean.matches(Regex(".*\\.(jpg|jpeg|png|webp|gif|bmp)$"))) {
        return ExtractedPage(URL(address).path.substringAfterLast('/'), address, listOf(address))
    }
    val doc = Jsoup.connect(address).userAgent("Mozilla/5.0 (compatible; ComicReader/1.0)").timeout(15000).get()
    val base = doc.baseUri()
    val images = doc.select("img[src], img[data-src], img[data-original]").mapNotNull { el ->
        val raw = el.attr("data-original").ifBlank { el.attr("data-src").ifBlank { el.attr("src") } }
        if (raw.isBlank()) null else runCatching { URL(URL(base), raw).toString() }.getOrNull()
    }.filter { it.startsWith("https://") || it.startsWith("http://") }.distinct()
    val og = doc.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf { it.isNotBlank() }
        ?.let { runCatching { URL(URL(base), it).toString() }.getOrNull() }
    return ExtractedPage(doc.title().ifBlank { URL(address).host }, og ?: images.firstOrNull().orEmpty(), images)
}

private fun historyJson(entries: List<ReadingEntry>) = JSONArray().apply {
    entries.forEach { e -> put(JSONObject().apply { put("url", e.url); put("title", e.title); put("cover", e.cover); put("lastIndex", e.lastIndex) }) }
}.toString()

private fun loadHistory(prefs: android.content.SharedPreferences): List<ReadingEntry> = runCatching {
    val a = JSONArray(prefs.getString("history", "[]"))
    (0 until a.length()).map { i -> a.getJSONObject(i).let { ReadingEntry(it.optString("url"), it.optString("title"), it.optString("cover"), it.optInt("lastIndex")) } }
}.getOrDefault(emptyList())

private fun loadFavorites(prefs: android.content.SharedPreferences): List<String> = runCatching {
    val a = JSONArray(prefs.getString("favorites", "[]")); (0 until a.length()).map { a.getString(it) }
}.getOrDefault(emptyList())
